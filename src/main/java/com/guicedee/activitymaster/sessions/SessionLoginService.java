package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.google.inject.*;
import com.google.inject.name.Named;
//import com.google.inject.persist.Transactional;
import com.guicedee.activitymaster.fsdm.client.services.*;
import com.guicedee.activitymaster.fsdm.client.services.ReactiveTransactionUtil;
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
import com.guicedee.guicedinjection.pairing.Pair;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import io.smallrye.mutiny.Uni;

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
 * - ReactiveTransactionUtil is used for transaction management
 * - Exceptions are handled within reactive chains
 * - Parallel processing is used where appropriate
 */
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
 * - ReactiveTransactionUtil is used for transaction management
 * - Exceptions are handled within reactive chains
 * - Parallel processing is used where appropriate
 */
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
 * - ReactiveTransactionUtil is used for transaction management
 * - Exceptions are handled within reactive chains
 * - Parallel processing is used where appropriate
 */
public class SessionLoginService implements ISessionLoginService<SessionLoginService>
{
	private static final Logger log = Logger.getLogger(SessionLoginService.class.getName());
	
	@Inject
	private IEnterprise<?, ?> enterprise;
	
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
	
	@Inject
	@Named(SessionMasterSystemName)
	private Provider<ISystems<?, ?>> system;
	
	@Inject
	@Named(SessionMasterSystemName)
	private UUID identityToken;
	
//	@Inject
//	private ProfileServiceDTO<?> dto;
	
	@Override
	public Uni<ProfileServiceDTO<?>> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Login visitor with web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		
		// Handle identity token
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		final UUID[] finalIdentityToken = identityToken;
		ProfileServiceDTO<?> dto = profileServiceDTO;
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> {
			IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();
			
			// Find device IP
			return iInvolvedParty.builder()
				.findByType(TypeDevice.toString(), dto.getWebClientUUID().toString(), system, finalIdentityToken)
				.get()
				.onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
				.onItem().ifNull().switchTo(() -> {
					// Create device IP if not found
					return createDeviceIP(profileServiceDTO)
						.map(deviceIP -> {
							// Set involved party and identity token
							profileServiceDTO.setInvolvedParty(deviceIP);
							profileServiceDTO.setIdentityToken(deviceIP.getId());
							return deviceIP;
						}).map(result->result);
				})
				.chain(deviceIP -> {
					// Find involved party
					IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
					
					if (foundIPCurrentOnDevice == null) {
						// Set device IP as involved party
						profileServiceDTO.setInvolvedParty(deviceIP);
						profileServiceDTO.setIdentityToken(deviceIP.getId());
						foundIPCurrentOnDevice = deviceIP;
					} else {
						// Set found involved party
						profileServiceDTO.setInvolvedParty(foundIPCurrentOnDevice);
						profileServiceDTO.setIdentityToken(foundIPCurrentOnDevice.getId());
					}
					
					final IInvolvedParty<?, ?> finalFoundIPCurrentOnDevice = foundIPCurrentOnDevice;
					
					// Update last visit time
					return updateLatestVisit(finalFoundIPCurrentOnDevice, finalIdentityToken)
						.chain(updatedIP -> {
							// Get session
							return sessionMasterService.getSession(updatedIP, system, finalIdentityToken)
								.map(session -> {
									// Return profile service DTO
									return profileServiceDTO;
								});
						});
				})
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error in loginVisitor: {0}", error.getMessage()));
		});
	}
	
 /**
 * Authenticates a user with the given login DTO.
 * This method has been migrated to use reactive patterns.
 *
 * @param loginDTO The login DTO containing username and password
 * @return A Uni emitting the authenticated involved party
 */
Uni<IInvolvedParty<?, ?>> authenticate(UserLoginDTO<?> loginDTO)
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
	 * @param profileServiceDTO The profile service DTO containing client information
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the found or created device IP
	 */
	private Uni<IInvolvedParty<?, ?>> findOrCreateDeviceIP(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Finding or creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		
		if (profileServiceDTO.getWebClientUUID() == null) {
			profileServiceDTO.setWebClientUUID(UUID.randomUUID());
		}
		
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0) 
			? new UUID[]{this.identityToken} 
			: identityToken;
		
		// Try to find existing device IP
		return involvedPartyService.get().builder()
			.findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, finalIdentityToken)
			.latestFirst()
			.setMaxResults(1)
			.get()
			.onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
			.onItem().ifNull().switchTo(() -> {
				// Create new device IP if not found
				log.log(Level.FINE, "Device IP not found, creating new one via loginVisitor");
				return loginVisitor(profileServiceDTO, system, finalIdentityToken)
					.chain(dto -> 
						involvedPartyService.get().builder()
							.findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, finalIdentityToken)
							.latestFirst()
							.setMaxResults(1)
							.get()
							.onItem().ifNull().failWith(() -> new InvolvedPartyException("Device IP must already exist before attempting to login"))
					);
			})
			.onFailure().invoke(error -> log.log(Level.SEVERE, "Error finding or creating device IP: {0}", error.getMessage()));
	}

	@Override
	public Uni<ProfileServiceDTO<?>> loginUser(UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Login user: {0}, already verified: {1}", new Object[]{profileServiceDTO.getUserName(), alreadyVerified});
		
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0 && profileServiceDTO.getIdentityToken() == null)
			? new UUID[]{this.identityToken}
			: identityToken;
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> 
			// Find or create device IP
			findOrCreateDeviceIP(profileServiceDTO, system, finalIdentityToken)
				.chain(deviceIP -> {
					// Authenticate user or get involved party
					Uni<IInvolvedParty<?, ?>> foundPartyUni;
					if (!alreadyVerified) {
						foundPartyUni = authenticate(profileServiceDTO)
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
										session, NoClassification.toString(),
								IdentificationTypeWebClientUUID.toString(), 
								webClientID,
								system, true, true, finalIdentityToken)
							.chain(involvedPartyIdentificationType -> {
								// Archive if present
								if (involvedPartyIdentificationType != null) {
									return involvedPartyIdentificationType.archive(session, system, finalIdentityToken)
										.chain(() -> Uni.createFrom().item(involvedPartyIdentificationType));
								} else {
									return Uni.createFrom().nullItem();
								}
							})
							.chain(involvedPartyIdentificationType -> 
								// Add child
								deviceIP.addChild(
										session, (IWarehouseTable<?, ?, ? extends Serializable, ?>) foundParty,
										DeviceUsedBy.toString(), 
										webClientID, 
										system, 
										finalIdentityToken)
							)
							.chain(() -> 
								// Add or update involved party identification type
								foundParty.addOrUpdateInvolvedPartyIdentificationType(
										session, NoClassification.toString(),
										IdentificationTypeWebClientUUID.toString(),
										webClientID, 
										webClientID,
										system, 
										finalIdentityToken)
							)
							.chain(() -> 
								// Set user logged in
								setUserLoggedIn(
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
					log.log(Level.SEVERE, "Error in loginUser: {0}", error.getMessage()))
		);
	}
	
	
	@Override
	public Uni<ProfileServiceDTO<?>> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		return loginUser(profileServiceDTO, false, system, identityToken);
	}
	
	@Override
	public Uni<ProfileServiceDTO<?>> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		var deviceIP
				= createDeviceIP(profileServiceDTO);
		
		var idWebClient
				= profileServiceDTO.findInvolvedParty()
				                   .findInvolvedPartyIdentificationType(session, NoClassification.toString(), IdentificationTypeWebClientUUID.toString(),
						                   profileServiceDTO.getWebClientUUID()
						                                    .toString(), system, false, false,
						                   this.identityToken);


		if (idWebClient.isPresent())
		{
			idWebClient.get()
			           .archive(system, identityToken);
			deviceIP.archiveChild((IWarehouseTable)idWebClient.get().getPrimary(), DeviceUsedBy.toString(), null, system, identityToken);
		}
		
		String uuid = profileServiceDTO.getWebClientUUID()
		                               .toString();
		deviceIP.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(), IdentificationTypeWebClientUUID.toString(),
				uuid,
				uuid,
				system, identityToken);
		
		setUserLoggedOut(profileServiceDTO.findInvolvedParty(), deviceIP, profileServiceDTO, system, identityToken);
		
		return profileServiceDTO;
	}
	
	
	@InvolvedPartyEvent(Added)
	@LogItemEvent(Added)
	@Override
	public Uni<Void> setUserLoggedIn(@Party("UserLoggingIn") IInvolvedParty<?, ?> newIp,
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
		return sessionMasterService.getSession(newIp, system, finalIdentityToken)
				.chain(session -> {
					// Set session values
					return session.setInvolvedParty(newIp)
							.map(updatedSession -> {
								updatedSession.addValue(IDENTITY_SESSION_NAME, dto);
								updatedSession.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
								updatedSession.addValue(USER_ROLES_SESSION_NAME, rolesService.getRoles(newIp, system, finalIdentityToken));
								return updatedSession;
							});
				})
				.chain(session -> {
					// Update session and remove cache
					return sessionMasterService.updateSession(newIp, session, system, finalIdentityToken)
							.chain(updatedSession -> sessionMasterService.removeCache(newIp));
				})
				.onFailure().invoke(error -> {
					log.log(Level.SEVERE, "Cannot create user session", error);
				})
				.chain(() -> {
					// Update DTO
					dto.setEnterprise(enterprise);
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
	public Uni<Void> setUserLoggedOut(@Party("UserLoggedOut") IInvolvedParty<?, ?> involvedParty,
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
		return sessionMasterService.getSession(involvedParty, system, finalIdentityToken)
				.chain(session -> {
					// Update session values
					session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
					session.removeValue(USER_ROLES_SESSION_NAME);
					
					// Update session for involved party
					return sessionMasterService.updateSession(involvedParty, session, system, finalIdentityToken);
				})
				.chain(session -> {
					// Set profile service dto to the device IP
					profileServiceDTO.setIdentityToken(deviceIP.getId());
					profileServiceDTO.setInvolvedParty(deviceIP);
					
					// Update session for device IP
					return session.setInvolvedParty(deviceIP)
							.chain(updatedSession -> 
								sessionMasterService.updateSession(deviceIP, updatedSession, system, finalIdentityToken)
							);
				})
				.onFailure().invoke(error -> {
					log.log(Level.SEVERE, "Error in setUserLoggedOut: " + error.getMessage(), error);
				})
				.chain(() -> Uni.createFrom().voidItem());
	}
	
	@Override
	public Uni<UserConfirmationKeyDTO<?>> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Registering visitor: {0}", userRegistrationDTO.getUserName());
		
		// Handle identity token
		final UUID[] finalIdentityToken = (identityToken == null || identityToken.length == 0)
			? new UUID[]{this.identityToken}
			: identityToken;
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> {
			// Check if user already exists
			return involvedPartyService.get().builder()
				.findByIdentificationType(
						IdentificationTypeUserName.toString(), 
						userRegistrationDTO.getUserName(), 
						system, 
						finalIdentityToken)
				.get()
				.onItem().ifNotNull().transformToUni(ipExists -> {
					// Check if user has confirmation key
					return ipExists.hasClassifications(session, ConfirmationKey, null, system, finalIdentityToken)
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
									session, NoClassification.toString(),
							IdentificationTypeUserName,
							userRegistrationDTO.getUserName(),
							userRegistrationDTO.getUserName(),
							system,
							this.identityToken)
						.chain(idType -> {
							// Expire identification type
							return idType.expire(session, Duration.of(2, HOURS), finalIdentityToken)
								.chain(() -> {
									// Add username and password
									return passwordsService.addUpdateUsernamePassword(
											session, userRegistrationDTO.getUserName(),
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
									session, NoClassification.toString(),
								IdentificationTypeUserName,
								userRegistrationDTO.getUserName(),
								userRegistrationDTO.getUserName(),
								system,
								this.identityToken);
						})
						.chain(idUserNameType -> {
							// Expire username identification type
							return idUserNameType.expire(session, Duration.of(2, HOURS), finalIdentityToken);
						})
						.chain(() -> {
							// Find security password classifications
							return newIp.findClassification(session, SecurityPassword, system, finalIdentityToken)
								.chain(classification -> {
									if (classification != null) {
										return classification.expire(session, Duration.of(2, HOURS), this.identityToken);
									} else {
										return Uni.createFrom().voidItem();
									}
								});
						})
						.chain(() -> {
							return newIp.findClassification(session, SecurityPasswordSalt, system, finalIdentityToken)
								.chain(classification -> {
									if (classification != null) {
										return classification.expire(session, Duration.of(2, HOURS), this.identityToken);
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
											session, ConfirmationKey,
									null, 
									confirmationKeyDTO.getConfirmationKey() + "", 
									system, 
									this.identityToken)
								.chain(() -> {
									// Find and expire confirmation key
									return newIp.findClassification(session, ConfirmationKey, system, finalIdentityToken)
										.chain(classification -> {
											if (classification != null) {
												return classification.expire(session, Duration.of(2, HOURS), this.identityToken)
													.map(v -> confirmationKeyDTO);
											} else {
												return Uni.createFrom().item(confirmationKeyDTO);
											}
										});
								});
						});
				})
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error registering visitor: {0}", error.getMessage()));
		});
	}
	
	/**
	 * Creates a device involved party for the given profile service DTO.
	 * This method has been migrated to use reactive patterns.
	 *
	 * @param profileServiceDTO The profile service DTO containing client information
	 * @return A Uni emitting the created or found device involved party
	 */
	Uni<IInvolvedParty<?, ?>> createDeviceIP(ProfileServiceDTO<?> profileServiceDTO)
	{
		log.log(Level.FINE, "Creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
		String webClientUUID = profileServiceDTO.getWebClientUUID().toString();
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> {
			// Find device type
			return involvedPartyService.findType(session, TypeDevice.toString(), system.get(), identityToken)
				.chain(deviceType -> {
					// Try to find existing device IP
					return involvedPartyService.get().builder()
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
							return involvedPartyService.create(session, system.get(), deviceIDType, false, identityToken)
								.chain(newIp -> {
									// Add involved party type
									return newIp.addOrReuseInvolvedPartyType(
													session, NoClassification.toString(),
											deviceType, 
											webClientUUID, 
											system.get(), 
											identityToken)
										.chain(() -> Uni.createFrom().item(newIp));
								});
						});
				})
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error creating device IP: {0}", error.getMessage()));
		});
	}
	
	/**
	 * Updates the last visit time for an involved party.
	 * This method has been migrated to use reactive patterns.
	 *
	 * @param newIp The involved party to update
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the updated involved party
	 */
	Uni<IInvolvedParty<?, ?>> updateLatestVisit(IInvolvedParty<?, ?> newIp,
	                                       java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Updating last visit time for involved party: {0}", newIp.getId());
		String lastVisit = convertToUTCDateTime(com.entityassist.RootEntity.getNow()).format(DateTimeFormatter.ISO_DATE);
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> {
			// Use addOrUpdateClassification which already returns a reactive type
			return newIp.addOrUpdateClassification(session, LastVisitTime,
					null,
					lastVisit,
					system.get(),
					identityToken)
				// Chain with Uni.createFrom().item(newIp) to return the involved party
				.chain(() -> Uni.createFrom().item(newIp))
				// Add error handling with onFailure().invoke()
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error updating last visit time: {0}", error.getMessage()));
		});
	}
	
	
	@Override
	public Uni<Boolean> verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		log.log(Level.FINE, "Verifying if username exists: {0}", userLoginDTO.getUserName());
		if (Strings.isNullOrEmpty(userLoginDTO.getUserName()))
		{
			return Uni.createFrom().failure(new ProfileServiceException("Username cannot be empty"));
		}
		return passwordsService.doesUsernameExist(session, userLoginDTO.getUserName(), system, identityToken)
			.onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying username: {0}", error.getMessage()));
	}
	
	@Override
	public Uni<UserLoginDTO<?>> verifyPasswordForUser(UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, java.util.UUID... identityToken)
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
		
		// Use ReactiveTransactionUtil for transaction management
		return ReactiveTransactionUtil.withTransaction(session -> {
			// Find involved party by username and password
			return passwordsService.findByUsernameAndPassword(
							session, userLoginDTO.getUserName(),
					userLoginDTO.getPassword(),
					system.get(), 
					true, 
					identityToken)
				.map(ip -> {
					// Create and return new UserLoginDTO with identity token
					return new UserLoginDTO<>().setIdentityToken(ip.getId());
				})
				.onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying password: {0}", error.getMessage()));
		});
	}
	
	////@Transactional()
	/*IInvolvedParty<?, ?> configureFromHTTPServletRequest(UserDTO<?> dto, IInvolvedParty<?, ?> ip, ISystems<?, ?> profileSystem, HttpServletRequest servletRequest, IEnterprise<?, ?> enterprise)
	{
		StringBuilder sb = new StringBuilder();
		Enumeration<String> headerNames = servletRequest.getHeaderNames();
		while (headerNames.hasMoreElements())
		{
			String h = headerNames.nextElement();
			String v = servletRequest.getHeader(h);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(h, v);
			sb.append(jsonObject);
		}
		
		IAddressService<?> addressService = com.guicedee.client.IGuiceContext.get(IAddressService.class);
		String ipReal = servletRequest.getRemoteAddr();
		if (ipReal.equalsIgnoreCase("0:0:0:0:0:0:0:1"))
		{
			InetAddress inetAddress = null;
			try
			{
				inetAddress = InetAddress.getLocalHost();
			}
			catch (UnknownHostException e)
			{
				LogFactory.getLog("ConfigureFromServletRequest")
				          .log(Level.SEVERE, "Unknown host in getting INet Address for localhost ipv6", e);
			}
			String ipAddress = inetAddress.getHostAddress();
			ipReal = ipAddress;
		}
		IAddress<?, ?> ipAddress = addressService.addOrFindIPAddress(ipReal, profileSystem, identityToken);
		ip.addOrReuseAddress(ipAddress, RemoteAddressIPAddress.toString(), ipReal, ipReal, profileSystem, identityToken);
		IAddress<?, ?> hostName = addressService.addOrFindHostName(servletRequest.getRemoteHost(), profileSystem, identityToken);
		ip.addOrReuseAddress(hostName, RemoteAddressHostName.toString(), servletRequest.getRemoteHost(), servletRequest.getRemoteHost(), profileSystem, identityToken);
		IAddress<?, ?> localIpAddress = addressService.addOrFindHostName(servletRequest.getLocalAddr(), profileSystem, identityToken);
		ip.addOrReuseAddress(localIpAddress, LocalAddressIPAddress.toString(), servletRequest.getLocalAddr(), servletRequest.getLocalAddr(), profileSystem, identityToken);
		IAddress<?, ?> localHostName = addressService.addOrFindHostName(servletRequest.getLocalName(), profileSystem, identityToken);
		ip.addOrReuseAddress(localHostName, LocalAddressHostName.toString(), servletRequest.getLocalName(), servletRequest.getLocalName(), profileSystem, identityToken);
		IAddress<?, ?> webAddress = addressService.addOrFindWebAddress(servletRequest.getRequestURL()
		                                                                             .toString(), profileSystem, identityToken);
		ip.addOrReuseAddress(webAddress, WebAddress.toString(), servletRequest.getRequestURL()
		                                                                      .toString(), servletRequest.getRequestURL()
		                                                                                                 .toString(), profileSystem, identityToken);
		return ip;
	}
	
	////@Transactional()
	IInvolvedParty<?, ?> configureFromReadableUserAgent(IInvolvedParty<?, ?> ip, ReadableUserAgent readableUserAgent, ISystems<?, ?> profileSystem, java.util.UUID... identityToken)
	{
		ip.addOrReuseClassification(BrowserDeviceCategory, readableUserAgent.getDeviceCategory()
		                                                                    .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(BrowserDevice, readableUserAgent.getDeviceCategory()
		                                                            .getCategory()
		                                                            .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(OperatingSystem, readableUserAgent.getOperatingSystem()
		                                                              .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(OperatingSystemFamily, readableUserAgent.getOperatingSystem()
		                                                                    .getFamily()
		                                                                    .getName(), profileSystem, identityToken);
		return ip;
	}*/
	
}
