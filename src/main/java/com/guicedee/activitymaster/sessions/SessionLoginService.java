package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.google.inject.*;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.fsdm.client.services.*;
import com.guicedee.activitymaster.fsdm.client.services.annotations.*;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.IWarehouseTable;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.InvolvedPartyException;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.SecurityAccessException;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.services.interfaces.IRolesService;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.*;
import com.guicedee.activitymaster.sessions.util.SessionUtil;
import com.guicedee.guicedinjection.pairing.Pair;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import jakarta.persistence.NoResultException;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guicedee.activitymaster.fsdm.client.services.annotations.EventAction.*;
import static com.guicedee.activitymaster.fsdm.client.services.builders.IQueryBuilderSCD.convertToUTCDateTime;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.DefaultClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.InvolvedPartyClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.types.IPTypes.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.types.IdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileIdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.services.interfaces.IRolesService.*;
import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static java.time.temporal.ChronoUnit.*;

/**
 * Service for handling session login operations.
 * 
 * This class has been migrated to use reactive patterns with Mutiny's Uni.
 * All methods now return Uni types and use reactive composition for better
 * scalability and resource utilization.
 * 
 * Key changes in this migration:
 * - All methods return Uni types instead of blocking directly
 * - Blocking operations are replaced with reactive alternatives
 * - All methods accept a Mutiny.Session parameter for database operations
 * - Sessions are passed through the chain rather than created within methods
 * - Exceptions are handled within reactive chains
 */
@SuppressWarnings("unchecked")
public class SessionLoginService implements ISessionLoginService<SessionLoginService>
{
	private static final Logger log = Logger.getLogger(SessionLoginService.class.getName());

	@Inject
	private IUserSessionService<?> sessionMasterService;
	
	@Inject
	private ISecurityTokenService<?> securityTokenService;
	
	@Inject
	private IRolesService<?> rolesService;
	
	@Inject
	private IInvolvedPartyService<?> involvedPartyService;
	
	@Inject
	private IPasswordsService<?> passwordsService;
	
 // TODO: Remove these injected fields after full migration to reactive pattern
 // These fields are being replaced by calls to getISystem and getISystemToken
 // They are kept temporarily as fallbacks during the migration
 @Inject
 @Named(SessionMasterSystemName)
 private Provider<ISystems<?, ?>> system;

 @Inject
 @Named(SessionMasterSystemName)
 private UUID identityToken;
	
	@Override
	public Uni<ProfileServiceDTO<?>> loginVisitor(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Login visitor with web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		
		// Handle identity token
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		final UUID[] finalIdentityToken = identityToken;
		
		// Create a final reference to the DTO for use in lambda expressions
		final ProfileServiceDTO<?> dto = profileServiceDTO;
		
		// Get the involved party service
		IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();
		
		// Find device IP using the builder with session parameter
		Uni<IInvolvedParty<?, ?>> deviceIPUni = (Uni) iInvolvedParty.builder(session)
			.findByType(TypeDevice.toString(), dto.getWebClientUUID().toString(), system, finalIdentityToken)
			.get();
		
		// Handle NoResultException by creating a new device IP
		deviceIPUni = deviceIPUni.onFailure(NoResultException.class).recoverWithUni(() -> {
			log.log(Level.FINE, "Device IP not found, creating new one");
			return createDeviceIP(session, dto);
		});
		
		// Handle any other failures
		deviceIPUni = deviceIPUni.onFailure().invoke(error -> {
			if (!(error instanceof NoResultException)) {
				log.log(Level.SEVERE, "Error finding device IP: {0}", String.valueOf(error));
			}
		});
		
		// Process the device IP and continue the chain
		Uni<ProfileServiceDTO<?>> result = deviceIPUni.chain(deviceIP -> {
			// Set device IP as involved party
			dto.setInvolvedParty(deviceIP);
			dto.setIdentityToken(deviceIP.getId());
			
			// Find involved party
			IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
			
			if (foundIPCurrentOnDevice == null) {
				// Use device IP if no involved party found
				foundIPCurrentOnDevice = deviceIP;
			} else {
				// Set found involved party
				dto.setInvolvedParty(foundIPCurrentOnDevice);
				dto.setIdentityToken(foundIPCurrentOnDevice.getId());
			}
			
			// Update last visit time
			return updateLatestVisit(session, foundIPCurrentOnDevice, finalIdentityToken);
		})
		.chain(updatedIP -> {
			// Get session
			return sessionMasterService.getSession(session, updatedIP, system, finalIdentityToken);
		})
		.map(userSession -> {
			// Return the DTO
			return dto;
		});
		
		// Add error handling
		result = result.onFailure().invoke(error -> {
			// Log any failures
			log.log(Level.SEVERE, "Error in loginVisitor: {0}", String.valueOf(error));
		});
		
		return result;
	}
	
	/**
	 * Authenticates a user with the given login DTO.
	 * This method has been migrated to use reactive patterns.
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param loginDTO The login DTO containing username and password
	 * @return A Uni emitting the authenticated involved party
	 */
	Uni<IInvolvedParty<?, ?>> authenticate(Mutiny.Session session, UserLoginDTO<?> loginDTO)
	{
		return passwordsService.findByUsernameAndPassword(session, loginDTO.getUserName(),
				loginDTO.getPassword(),
				system.get(),
				true,
				identityToken);
	}
	
	/**
	 * Helper method to find or create a device IP for the given profile service DTO.
	 * This method simplifies the loginUser implementation.
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The profile service DTO containing client information
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the found or created device IP
	 */
	private Uni<IInvolvedParty<?, ?>> findOrCreateDeviceIP(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Finding or creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		
		if (profileServiceDTO.getWebClientUUID() == null) {
			profileServiceDTO.setWebClientUUID(UUID.randomUUID());
		}
		
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0) 
			? new UUID[]{this.identityToken} 
			: identityToken;
		
		// Try to find existing device IP
		return (Uni) involvedPartyService.get().builder(session)
			.findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, finalIdentityToken)
			.latestFirst()
			.setMaxResults(1)
			.get()
			.onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
			.onItem().ifNull().switchTo(() -> {
				// Create new device IP if not found
				log.log(Level.FINE, "Device IP not found, creating new one via loginVisitor");
				return (Uni) loginVisitor(session, profileServiceDTO, system, finalIdentityToken)
					.chain(dto -> 
						involvedPartyService.get().builder(session)
							.findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, finalIdentityToken)
							.latestFirst()
							.setMaxResults(1)
							.get()
							.onItem().ifNull().failWith(() -> new InvolvedPartyException("Device IP must already exist before attempting to login"))
					);
			})
			.onFailure().invoke(error -> log.log(Level.SEVERE, "Error finding or creating device IP: {0}", String.valueOf(error)));
	}

	@Override
	public Uni<ProfileServiceDTO<?>> loginUser(Mutiny.Session session, UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Login user: {0}, already verified: {1}", new Object[]{profileServiceDTO.getUserName(), alreadyVerified});
		
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0 && profileServiceDTO.getIdentityToken() == null)
			? new UUID[]{this.identityToken}
			: identityToken;
		
		// Use SessionUtil to execute with the provided session
		return (Uni) SessionUtil.executeWithSession(session, dbSession ->
			// Find or create device IP
			findOrCreateDeviceIP(dbSession, profileServiceDTO, system, finalIdentityToken)
				.chain(deviceIP -> {
					// Authenticate user or get involved party
					Uni<IInvolvedParty<?, ?>> foundPartyUni;
					if (!alreadyVerified) {
						foundPartyUni = authenticate(dbSession, profileServiceDTO)
							.map(foundParty -> {
								profileServiceDTO.setPassword(null);
								return foundParty;
							});
					} else {
						foundPartyUni = Uni.createFrom().item(profileServiceDTO.findInvolvedParty());
					}
					
					return foundPartyUni.chain(foundParty -> {
						// Move the webclient uuid to the new user
						String webClientID = profileServiceDTO.getWebClientUUID().toString();
						
						// Find involved party identification type
						return deviceIP.findInvolvedPartyIdentificationType(
										dbSession, NoClassification.toString(),
								IdentificationTypeWebClientUUID.toString(), 
								webClientID,
								system, true, true, finalIdentityToken)
							.chain(involvedPartyIdentificationType -> {
								// Archive if present
								if (involvedPartyIdentificationType != null) {
									return involvedPartyIdentificationType.archive(dbSession, system, finalIdentityToken)
										.chain(() -> Uni.createFrom().item(involvedPartyIdentificationType));
								} else {
									return Uni.createFrom().nullItem();
								}
							})
							.chain(involvedPartyIdentificationType -> 
								// Add child
								deviceIP.addChild(
										dbSession, (IWarehouseTable<?, ?, ? extends Serializable, ?>) foundParty,
										DeviceUsedBy.toString(), 
										webClientID, 
										system, 
										finalIdentityToken)
							)
							.chain(() -> 
								// Add or update involved party identification type
								foundParty.addOrUpdateInvolvedPartyIdentificationType(
										dbSession, NoClassification.toString(),
										IdentificationTypeWebClientUUID.toString(),
										webClientID, 
										webClientID,
										system, 
										finalIdentityToken)
							)
							.chain(() -> 
								// Set user logged in
								setUserLoggedIn(
										dbSession,
										foundParty, 
										profileServiceDTO, 
										profileServiceDTO.isRememberMe(), 
										system, 
										this.identityToken)
									.map(v -> profileServiceDTO)
							);
					});
				})
				.onFailure(SecurityAccessException.class).transform(e -> 
					new ProfileServiceException("Invalid username or password"))
				.onFailure().invoke(error -> 
					log.log(Level.SEVERE, "Error in loginUser: {0}", String.valueOf(error)))
		);
	}
	
	@Override
	public Uni<ProfileServiceDTO<?>> loginUser(Mutiny.Session session, UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		return loginUser(session, profileServiceDTO, false, system, identityToken);
	}
	
	@Override
	public Uni<ProfileServiceDTO<?>> logoutUser(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		final UUID[] finalIdentityToken = identityToken;
		
		// Use SessionUtil to execute with the provided session
		return SessionUtil.executeWithSession(session, dbSession -> {
			// Create device IP
			return createDeviceIP(dbSession, profileServiceDTO)
				.chain(deviceIP -> {
					// Find involved party identification type
					return profileServiceDTO.findInvolvedParty()
						.findInvolvedPartyIdentificationType(
							dbSession, 
							NoClassification.toString(), 
							IdentificationTypeWebClientUUID.toString(),
							profileServiceDTO.getWebClientUUID().toString(), 
							system, 
							false, 
							false,
							this.identityToken)
						.chain(idWebClientOpt -> {
							// Archive if present
							if (idWebClientOpt != null) {
								return idWebClientOpt.archive(dbSession, system, finalIdentityToken)
									.chain(() -> deviceIP.archiveChild(
										dbSession,
										(IWarehouseTable)idWebClientOpt.getPrimary(), 
										DeviceUsedBy.toString(), 
										null, 
										system, 
										finalIdentityToken)
									)
									.map(v -> (IInvolvedParty<?,?>) deviceIP);
							} else {
								return Uni.createFrom().item((IInvolvedParty<?,?>)deviceIP);
							}
						});
				})
				.chain(deviceIP -> {
					// Add or update involved party identification type
					String uuid = profileServiceDTO.getWebClientUUID().toString();
					return ((IInvolvedParty<?,?>)deviceIP).addOrUpdateInvolvedPartyIdentificationType(
						dbSession,
						NoClassification.toString(), 
						IdentificationTypeWebClientUUID.toString(),
						uuid,
						uuid,
						system, 
						finalIdentityToken)
						.chain(idType -> 
							// Set user logged out
							setUserLoggedOut(
								dbSession,
								profileServiceDTO.findInvolvedParty(),
									((IInvolvedParty<?,?>)deviceIP),
								profileServiceDTO, 
								system, 
								finalIdentityToken)
							.map(v -> profileServiceDTO)
						);
				});
		});
	}
	
	@InvolvedPartyEvent(Added)
	@LogItemEvent(Added)
	@Override
	public Uni<Void> setUserLoggedIn(Mutiny.Session session, @Party("UserLoggingIn") IInvolvedParty<?, ?> newIp,
	                            @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
	                            boolean rememberMe,
	                            @Party("SystemPerformed") ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		// Normalize identity token
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0 && profileServiceDTO.getIdentityToken() == null)
				? new UUID[]{this.identityToken}
				: identityToken;
		
		// Update profile service DTO
		profileServiceDTO.setInvolvedParty(newIp);
		profileServiceDTO.setIdentityToken(newIp.getId());
		ProfileServiceDTO<?> dto = profileServiceDTO;
		
		// Create user security DTO
		UserSecurityDTO us = new UserSecurityDTO()
				.setLoggedIn(true)
				.setRememberMe(rememberMe)
				.setLoginExpiresOn(rememberMe
						? LocalDateTime.MAX
						: com.entityassist.RootEntity.getNow().plusMinutes(20));
		
		// Get session and update it reactively
		return sessionMasterService.getSession(session, newIp, system, finalIdentityToken)
				.chain(userSession -> {
					// Set session values
					return userSession.setInvolvedParty(newIp)
							.map(updatedSession -> {
								updatedSession.addValue(IDENTITY_SESSION_NAME, dto);
								updatedSession.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
								updatedSession.addValue(USER_ROLES_SESSION_NAME, rolesService.getRoles(session, newIp, system, finalIdentityToken));
								return updatedSession;
							});
				})
				.chain(userSession -> {
					// Update session and remove cache
					return sessionMasterService.updateSession(session, newIp, userSession, system, finalIdentityToken)
							.chain(updatedSession -> sessionMasterService.removeCache(session, newIp));
				})
				.onFailure().invoke(error -> {
					log.log(Level.SEVERE, "Cannot create user session", error);
				})
				.chain(() -> {
					// Update DTO
					dto.setInvolvedParty(newIp);
					dto.setWebClientUUID(profileServiceDTO.getWebClientUUID());
					dto.setIdentityToken(profileServiceDTO.getIdentityToken());
					dto.setEnterprise(system.getEnterprise());
					
					try {
						IJsonRepresentation.getObjectMapper()
								.readerForUpdating(dto)
								.readValue(dto.toJson());
					} catch (Exception e) {
						log.log(Level.SEVERE, "Session Master cannot update DTO from profile", e);
					}
					
					return Uni.createFrom().voidItem();
				});
	}
	
	@InvolvedPartyEvent(value = Added)
	@LogItemEvent(value = Added)
	@Override
	public Uni<Void> setUserLoggedOut(Mutiny.Session session, @Party("UserLoggedOut") IInvolvedParty<?, ?> involvedParty,
	                             @Party("DeviceUsedBy") IInvolvedParty<?, ?> deviceIP,
	                             @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
	                             @Party("SystemPerformed") ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		// Normalize identity token
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0)
				? new UUID[]{this.identityToken}
				: identityToken;
		
		// Create user security DTO
		UserSecurityDTO us = com.guicedee.client.IGuiceContext.get(UserSecurityDTO.class);
		us.setRememberMe(false);
		us.setLoggedIn(false);
		us.setLoginExpiresOn(com.entityassist.RootEntity.getNow());
		
		// Get session and update it reactively
		return sessionMasterService.getSession(session, involvedParty, system, finalIdentityToken)
				.chain(userSession -> {
					// Update session values
					userSession.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
					userSession.removeValue(USER_ROLES_SESSION_NAME);
					
					// Update session for involved party
					return sessionMasterService.updateSession(session, involvedParty, userSession, system, finalIdentityToken);
				})
				.chain(userSession -> {
					// Set profile service dto to the device IP
					profileServiceDTO.setIdentityToken(deviceIP.getId());
					profileServiceDTO.setInvolvedParty(deviceIP);
					
					// Update session for device IP
					return userSession.setInvolvedParty(deviceIP)
							.chain(updatedSession -> 
								sessionMasterService.updateSession(session, deviceIP, updatedSession, system, finalIdentityToken)
							);
				})
				.onFailure().invoke(error -> {
					log.log(Level.SEVERE, "Error in setUserLoggedOut: " + String.valueOf(error), error);
				})
				.chain(() -> Uni.createFrom().voidItem());
	}
	
	@Override
	public Uni<UserConfirmationKeyDTO<?>> registerVisitor(Mutiny.Session session, UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Registering visitor: {0}", userRegistrationDTO.getUserName());
		
		// Handle identity token
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0)
			? new UUID[]{this.identityToken}
			: identityToken;
		
		// Use SessionUtil to execute with the provided session
		return (Uni) SessionUtil.executeWithSession(session, dbSession -> {
			// Check if user already exists
					return involvedPartyService.get().builder(dbSession)
				.findByIdentificationType(
						IdentificationTypeUserName.toString(), 
						userRegistrationDTO.getUserName(), 
						system, 
						finalIdentityToken)
				.get()
				.onItem().ifNotNull().transformToUni(ipExists -> {
					// Check if user has confirmation key
					return ipExists.hasClassifications(dbSession, ConfirmationKey, null, system, finalIdentityToken)
						.chain(hasConfirmationKey -> {
							if (hasConfirmationKey) {
								return Uni.createFrom().failure(
									new WaitingForConfirmationKeyException("The email address is waiting for a confirmation key"));
							} else {
								return Uni.createFrom().failure(
									new UserExistsException("That email address is already in use as a valid identifier"));
							}
						});
				})
				.onItem().ifNull().switchTo(() -> {
					// Create new involved party
					ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
					IInvolvedParty<?, ?> newIp = dto.findInvolvedParty();
					
					// Add identification type
					return newIp.addOrUpdateInvolvedPartyIdentificationType(
									dbSession, NoClassification.toString(),
							IdentificationTypeUserName,
							userRegistrationDTO.getUserName(),
							userRegistrationDTO.getUserName(),
							system,
							this.identityToken)
						.chain(idType -> {
							// Expire identification type
							return idType.expire(dbSession, Duration.of(2, HOURS), finalIdentityToken)
								.chain(() -> {
									// Add username and password
									return passwordsService.addUpdateUsernamePassword(
											dbSession, userRegistrationDTO.getUserName(),
										userRegistrationDTO.getPassword(), 
										newIp, 
										system,
										this.identityToken);
								});
						})
						.chain(updatedIp -> {
							// Clear password for security
							userRegistrationDTO.setPassword(null);
							
							// Add username identification type
							return newIp.addOrUpdateInvolvedPartyIdentificationType(
									dbSession, NoClassification.toString(),
								IdentificationTypeUserName,
								userRegistrationDTO.getUserName(),
								userRegistrationDTO.getUserName(),
								system,
								this.identityToken);
						})
						.chain(idUserNameType -> {
							// Expire username identification type
							return idUserNameType.expire(dbSession, Duration.of(2, HOURS), finalIdentityToken);
						})
						.chain(() -> {
							// Find security password classifications
							return newIp.findClassification(dbSession, SecurityPassword, system, finalIdentityToken)
								.chain(classification -> {
									if (classification != null) {
										return classification.expire(dbSession, Duration.of(2, HOURS), this.identityToken);
									} else {
										return Uni.createFrom().voidItem();
									}
								});
						})
						.chain(() -> {
							return newIp.findClassification(dbSession, SecurityPasswordSalt, system, finalIdentityToken)
								.chain(classification -> {
									if (classification != null) {
										return classification.expire(dbSession, Duration.of(2, HOURS), this.identityToken);
									} else {
										return Uni.createFrom().voidItem();
									}
								});
						})
						.chain(() -> {
							// Create confirmation key
							UserConfirmationKeyDTO<?> confirmationKeyDTO = new UserConfirmationKeyDTO<>()
								.setWebClientUUID(userRegistrationDTO.getWebClientUUID())
								.setIdentityToken(newIp.getId());
							confirmationKeyDTO.setConfirmationKey(String.valueOf(UUID.randomUUID()));
							
							// Add confirmation key classification
							return newIp.addOrUpdateClassification(
											dbSession, ConfirmationKey,
									null, 
									confirmationKeyDTO.getConfirmationKey() + "", 
									system, 
									this.identityToken)
								.chain(() -> {
									// Find and expire confirmation key
									return newIp.findClassification(dbSession, ConfirmationKey, system, finalIdentityToken)
										.chain(classification -> {
											if (classification != null) {
												return classification.expire(dbSession, Duration.of(2, HOURS), this.identityToken)
													.map(v -> confirmationKeyDTO);
											} else {
												return Uni.createFrom().item(confirmationKeyDTO);
											}
										});
								});
						});
				})
    .onFailure().invoke(error -> log.log(Level.SEVERE, "Error registering visitor: {0}", String.valueOf(error)));
		});
	}
	
	/**
	 * Creates a device involved party for the given profile service DTO.
	 * This method has been migrated to use reactive patterns.
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The profile service DTO containing client information
	 * @return A Uni emitting the created or found device involved party
	 */
	Uni<IInvolvedParty<?, ?>> createDeviceIP(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO)
	{
		log.log(Level.FINE, "Creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		String webClientUUID = profileServiceDTO.getWebClientUUID().toString();
		
		// Use SessionUtil to execute with the provided session
		return (Uni) SessionUtil.executeWithSession(session, dbSession -> {
			// Find device type
			return involvedPartyService.findType(dbSession, TypeDevice.toString(), system.get(), identityToken)
				.chain(deviceType -> {
					// Try to find existing device IP
					return involvedPartyService.get().builder(dbSession)
						.findByTypeAll(TypeDevice.toString(), webClientUUID, system.get(), identityToken)
						.latestFirst()
						.setMaxResults(1)
						.get()
						.onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
						.onItem().ifNull().switchTo(() -> {
							// Create new device IP if not found
							log.log(Level.FINE, "Device IP not found, creating new one");
							Pair<String, String> deviceIDType = new Pair<>();
							deviceIDType.setKey(IdentificationTypeWebClientUUID.toString())
									.setValue(webClientUUID);
							
							// Create involved party
							return (Uni) involvedPartyService.create(dbSession, system.get(), deviceIDType, false, identityToken)
								.chain(newIp -> {
									// Add involved party type
									return newIp.addOrReuseInvolvedPartyType(
													dbSession, NoClassification.toString(),
											deviceType, 
											webClientUUID, 
											system.get(), 
											identityToken)
										.chain(() -> Uni.createFrom().item(newIp));
								});
						});
				})
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error creating device IP: {0}", String.valueOf(error)));
		});
	}
	
	/**
	 * Updates the last visit time for an involved party.
	 * This method has been migrated to use reactive patterns.
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param newIp The involved party to update
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the updated involved party
	 */
	Uni<IInvolvedParty<?, ?>> updateLatestVisit(Mutiny.Session session, IInvolvedParty<?, ?> newIp,
	                                       java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Updating last visit time for involved party: {0}", newIp.getId());
		String lastVisit = convertToUTCDateTime(com.entityassist.RootEntity.getNow()).format(DateTimeFormatter.ISO_DATE);
		
		// Use SessionUtil to execute with the provided session
		return (Uni) SessionUtil.executeWithSession(session, dbSession -> {
			// Use addOrUpdateClassification which already returns a reactive type
			return newIp.addOrUpdateClassification(dbSession, LastVisitTime,
					null,
					lastVisit,
					system.get(),
					identityToken)
				// Chain with Uni.createFrom().item(newIp) to return the involved party
				.chain(() -> Uni.createFrom().item(newIp))
				// Add error handling with onFailure().invoke()
    .onFailure().invoke(error -> log.log(Level.SEVERE, "Error updating last visit time: {0}", String.valueOf(error)));
		});
	}
	
	@Override
	public Uni<Boolean> verifyUsernameExists(Mutiny.Session session, UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Verifying if username exists: {0}", userLoginDTO.getUserName());
		if (Strings.isNullOrEmpty(userLoginDTO.getUserName()))
		{
			return Uni.createFrom().failure(new ProfileServiceException("Username cannot be empty"));
		}
		return passwordsService.doesUsernameExist(session, userLoginDTO.getUserName(), system, identityToken)
   .onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying username: {0}", String.valueOf(error)));
	}
	
	@Override
	public Uni<UserLoginDTO<?>> verifyPasswordForUser(Mutiny.Session session, UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Verifying password for user: {0}", userLoginDTO.getUserName());
		
		// Validate input parameters
		if (Objects.isNull(userLoginDTO.getIdentityToken()))
		{
			return Uni.createFrom().failure(new ProfileServiceException("User Login DTO Already needs to have an associated UUID to login with a password"));
		}
		if (Strings.isNullOrEmpty(userLoginDTO.getPassword()))
		{
			return Uni.createFrom().failure(new ProfileServiceException("Passwords cannot be empty"));
		}
		
		// Use SessionUtil to execute with the provided session
		return (Uni) SessionUtil.executeWithSession(session, dbSession -> {
			// Find involved party by username and password
			return passwordsService.findByUsernameAndPassword(
							dbSession, userLoginDTO.getUserName(),
					userLoginDTO.getPassword(),
					system.get(), 
					true, 
					identityToken)
				.map(ip -> {
					// Create and return new UserLoginDTO with identity token
					return new UserLoginDTO<>().setIdentityToken(ip.getId());
				})
    .onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying password: {0}", String.valueOf(error)));
		});
	}
}