package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.google.inject.*;
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
import com.guicedee.client.utils.Pair;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
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
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static java.time.temporal.ChronoUnit.*;

/**
 * Service for handling session login operations.
 * <p>
 * This class has been migrated to use reactive patterns with Mutiny's Uni.
 * All methods now return Uni types and use reactive composition for better
 * scalability and resource utilization.
 * <p>
 * Key changes in this migration:
 * - All methods return Uni types instead of blocking directly
 * - Blocking operations are replaced with reactive alternatives
 * - All methods accept a Mutiny.StatelessSession parameter for database operations
 * - Sessions are passed through the chain rather than created within methods
 * - Exceptions are handled within reactive chains
 */
@SuppressWarnings("unchecked")
public class SessionLoginService implements ISessionLoginService<SessionLoginService> {
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

    @Override
    public Uni<ProfileServiceDTO<?>> loginVisitor(Mutiny.StatelessSession session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Login visitor (stateless) with web client UUID: {0}", profileServiceDTO.getWebClientUUID());

        final ProfileServiceDTO<?> dto = profileServiceDTO;
        IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();

        // Find the device (guest/visitor) involved party for this web-client UUID
        Uni<IInvolvedParty<?, ?>> deviceIPUni = (Uni) iInvolvedParty.builder(session)
                .findByType(TypeDevice.toString(), dto.getWebClientUUID().toString(), system, identityToken)
                .get();

        // Create one on demand when none exists yet
        deviceIPUni = deviceIPUni.onFailure(NoResultException.class).recoverWithUni(() -> {
            log.log(Level.FINE, "Device IP not found, creating new one (stateless)");
            return createDeviceIP(session, dto, system, identityToken);
        });

        deviceIPUni = deviceIPUni.onFailure().invoke(error -> {
            if (!(error instanceof NoResultException)) {
                log.log(Level.SEVERE, "Error finding device IP (stateless): {0}", String.valueOf(error));
            }
        });

        return (Uni) deviceIPUni.chain(deviceIP -> {
                    dto.setInvolvedParty(deviceIP);
                    dto.setIdentityToken(deviceIP.getId());

                    IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
                    if (foundIPCurrentOnDevice == null) {
                        foundIPCurrentOnDevice = deviceIP;
                    } else {
                        dto.setInvolvedParty(foundIPCurrentOnDevice);
                        dto.setIdentityToken(foundIPCurrentOnDevice.getId());
                    }

                    return updateLatestVisit(session, foundIPCurrentOnDevice, system, identityToken);
                })
                .chain(updatedIP -> sessionMasterService.getSession(session, updatedIP, system, identityToken))
                .map(userSession -> dto)
                .onFailure().invoke(error -> log.log(Level.SEVERE, "Error in loginVisitor (stateless): {0}", String.valueOf(error)));
    }

    /**
     * Authenticates a user with the given login DTO.
     * This method has been migrated to use reactive patterns.
     *
     * @param session  The Mutiny.StatelessSession to use for database operations
     * @param loginDTO The login DTO containing username and password
     * @return A Uni emitting the authenticated involved party
     */
    Uni<IInvolvedParty<?, ?>> authenticate(Mutiny.StatelessSession session, UserLoginDTO<?> loginDTO, ISystems<?, ?> system, UUID... identityToken) {
        return passwordsService.findByUsernameAndPassword(session, loginDTO.getUserName(),
                loginDTO.getPassword(),
                system,
                true,
                identityToken);
    }

    /**
     * Helper method to find or create a device IP for the given profile service DTO.
     * This method simplifies the loginUser implementation.
     *
     * @param session           The Mutiny.StatelessSession to use for database operations
     * @param profileServiceDTO The profile service DTO containing client information
     * @param system            The system requesting the operation
     * @param identityToken     The identity tokens for security
     * @return A Uni emitting the found or created device IP
     */
    private Uni<IInvolvedParty<?, ?>> findOrCreateDeviceIP(Mutiny.StatelessSession session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Finding or creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());

        if (profileServiceDTO.getWebClientUUID() == null) {
            profileServiceDTO.setWebClientUUID(UUID.randomUUID());
        }

        // Try to find existing device IP
        return (Uni) involvedPartyService.get().builder(session)
                .findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, identityToken)
                .latestFirst()
                .setMaxResults(1)
                .get()
                .onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
                .onItem().ifNull().switchTo(() -> {
                    // Create new device IP if not found
                    log.log(Level.FINE, "Device IP not found, creating new one via loginVisitor");
                    return (Uni) loginVisitor(session, profileServiceDTO, system, identityToken)
                            .chain(dto ->
                                    involvedPartyService.get().builder(session)
                                            .findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, identityToken)
                                            .latestFirst()
                                            .setMaxResults(1)
                                            .get()
                                            .onItem().ifNull().failWith(() -> new InvolvedPartyException("Device IP must already exist before attempting to login"))
                            );
                })
                .onFailure().invoke(error -> log.log(Level.SEVERE, "Error finding or creating device IP: {0}", String.valueOf(error)));
    }

    @Override
    public Uni<ProfileServiceDTO<?>> loginUser(Mutiny.StatelessSession session, UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Login user: {0}, already verified: {1}", new Object[]{profileServiceDTO.getUserName(), alreadyVerified});

        // Operate directly on the caller-provided session (this service never opens its own session/tx)
        return (Uni) withSession(session, dbSession ->
                // Find or create device IP
                findOrCreateDeviceIP(dbSession, profileServiceDTO, system, identityToken)
                        .chain(deviceIP -> {
                            // Authenticate user or get involved party
                            Uni<IInvolvedParty<?, ?>> foundPartyUni;
                            if (!alreadyVerified) {
                                foundPartyUni = authenticate(dbSession, profileServiceDTO, system, identityToken)
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
                                                system, true, true, identityToken)
                                        .chain(involvedPartyIdentificationType -> {
                                            // Archive if present
                                            if (involvedPartyIdentificationType != null) {
                                                return involvedPartyIdentificationType.archive(dbSession, system, identityToken)
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
                                                        identityToken)
                                        )
                                        .chain(() ->
                                                // Add or update involved party identification type
                                                foundParty.addOrUpdateInvolvedPartyIdentificationType(
                                                        dbSession, NoClassification.toString(),
                                                        IdentificationTypeWebClientUUID.toString(),
                                                        webClientID,
                                                        webClientID,
                                                        system,
                                                        identityToken)
                                        )
                                        .chain(() ->
                                                // Set user logged in
                                                setUserLoggedIn(
                                                        dbSession,
                                                        foundParty,
                                                        profileServiceDTO,
                                                        profileServiceDTO.isRememberMe(),
                                                        system,
                                                        identityToken)
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
    public Uni<ProfileServiceDTO<?>> loginUser(Mutiny.StatelessSession session, UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        return loginUser(session, profileServiceDTO, false, system, identityToken);
    }

    @Override
    public Uni<ProfileServiceDTO<?>> logoutUser(Mutiny.StatelessSession session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        // Operate directly on the caller-provided session (this service never opens its own session/tx)
        return withSession(session, dbSession -> {
            // Create device IP
            return createDeviceIP(dbSession, profileServiceDTO, system, identityToken)
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
                                        identityToken)
                                .chain(idWebClientOpt -> {
                                    // Archive if present
                                    if (idWebClientOpt != null) {
                                        return idWebClientOpt.archive(dbSession, system, identityToken)
                                                .chain(() -> deviceIP.archiveChild(
                                                        dbSession,
                                                        (IWarehouseTable) idWebClientOpt.getPrimary(),
                                                        DeviceUsedBy.toString(),
                                                        null,
                                                        system,
                                                        identityToken)
                                                )
                                                .map(v -> (IInvolvedParty<?, ?>) deviceIP);
                                    } else {
                                        return Uni.createFrom().item((IInvolvedParty<?, ?>) deviceIP);
                                    }
                                });
                    })
                    .chain(deviceIP -> {
                        // Add or update involved party identification type
                        String uuid = profileServiceDTO.getWebClientUUID().toString();
                        return ((IInvolvedParty<?, ?>) deviceIP).addOrUpdateInvolvedPartyIdentificationType(
                                        dbSession,
                                        NoClassification.toString(),
                                        IdentificationTypeWebClientUUID.toString(),
                                        uuid,
                                        uuid,
                                        system,
                                        identityToken)
                                .chain(idType ->
                                        // Set user logged out
                                        setUserLoggedOut(
                                                dbSession,
                                                profileServiceDTO.findInvolvedParty(),
                                                ((IInvolvedParty<?, ?>) deviceIP),
                                                profileServiceDTO,
                                                system,
                                                identityToken)
                                                .map(v -> profileServiceDTO)
                                );
                    });
        });
    }

    @InvolvedPartyEvent(Added)
    @LogItemEvent(Added)
    @Override
    public Uni<Void> setUserLoggedIn(Mutiny.StatelessSession session, @Party("UserLoggingIn") IInvolvedParty<?, ?> newIp,
                                     @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
                                     boolean rememberMe,
                                     @Party("SystemPerformed") ISystems<?, ?> system, java.util.UUID... identityToken) {
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
        return sessionMasterService.getSession(session, newIp, system, identityToken)
                .chain(userSession -> {
                    // Set session values
                    return userSession.setInvolvedParty(newIp)
                            .map(updatedSession -> {
                                updatedSession.addValue(IDENTITY_SESSION_NAME, dto);
                                updatedSession.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
                                updatedSession.addValue(USER_ROLES_SESSION_NAME, rolesService.getRoles(session, newIp, system, identityToken));
                                return updatedSession;
                            });
                })
                .chain(userSession -> {
                    // Update session and remove cache
                    return sessionMasterService.updateSession(session, newIp, userSession, system, identityToken)
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
    public Uni<Void> setUserLoggedOut(Mutiny.StatelessSession session, @Party("UserLoggedOut") IInvolvedParty<?, ?> involvedParty,
                                      @Party("DeviceUsedBy") IInvolvedParty<?, ?> deviceIP,
                                      @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
                                      @Party("SystemPerformed") ISystems<?, ?> system, java.util.UUID... identityToken) {
        // Create user security DTO
        UserSecurityDTO us = com.guicedee.client.IGuiceContext.get(UserSecurityDTO.class);
        us.setRememberMe(false);
        us.setLoggedIn(false);
        us.setLoginExpiresOn(com.entityassist.RootEntity.getNow());

        // Get session and update it reactively
        return sessionMasterService.getSession(session, involvedParty, system, identityToken)
                .chain(userSession -> {
                    // Update session values
                    userSession.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
                    userSession.removeValue(USER_ROLES_SESSION_NAME);

                    // Update session for involved party
                    return sessionMasterService.updateSession(session, involvedParty, userSession, system, identityToken);
                })
                .chain(userSession -> {
                    // Set profile service dto to the device IP
                    profileServiceDTO.setIdentityToken(deviceIP.getId());
                    profileServiceDTO.setInvolvedParty(deviceIP);

                    // Update session for device IP
                    return userSession.setInvolvedParty(deviceIP)
                            .chain(updatedSession ->
                                    sessionMasterService.updateSession(session, deviceIP, updatedSession, system, identityToken)
                            );
                })
                .onFailure().invoke(error -> {
                    log.log(Level.SEVERE, "Error in setUserLoggedOut: " + String.valueOf(error), error);
                })
                .chain(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<UserConfirmationKeyDTO<?>> registerVisitor(Mutiny.StatelessSession session, UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Registering visitor: {0}", userRegistrationDTO.getUserName());
        // Operate directly on the caller-provided session (this service never opens its own session/tx)
        return (Uni) withSession(session, dbSession -> {
            // Check if user already exists
            return involvedPartyService.get().builder(dbSession)
                    .findByIdentificationType(
                            IdentificationTypeUserName.toString(),
                            userRegistrationDTO.getUserName(),
                            system,
                            identityToken)
                    .get()
                    .onItem().ifNotNull().transformToUni(ipExists -> {
                        // Check if user has confirmation key
                        return ipExists.hasClassifications(dbSession, ConfirmationKey, null, system, identityToken)
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
                                        identityToken)
                                .chain(() -> newIp.findInvolvedPartyIdentificationType(
                                        dbSession, NoClassification, IdentificationTypeUserName,
                                        userRegistrationDTO.getUserName(), system, true, true, identityToken))
                                .chain(idType -> {
                                    // Expire identification type
                                    return idType.expire(dbSession, Duration.of(2, HOURS), identityToken)
                                            .chain(() -> {
                                                // Add username and password
                                                return passwordsService.addUpdateUsernamePassword(
                                                        dbSession, userRegistrationDTO.getUserName(),
                                                        userRegistrationDTO.getPassword(),
                                                        newIp,
                                                        system,
                                                        identityToken);
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
                                            identityToken);
                                })
                                            .chain(() -> newIp.findInvolvedPartyIdentificationType(
                                                    dbSession, NoClassification, IdentificationTypeUserName,
                                                    userRegistrationDTO.getUserName(), system, true, true, identityToken))
                                            .chain(idUserNameType -> {
                                    // Expire username identification type
                                    return idUserNameType.expire(dbSession, Duration.of(2, HOURS), identityToken);
                                })
                                .chain(() -> {
                                    // Find security password classifications
                                    return newIp.findClassification(dbSession, SecurityPassword, system, identityToken)
                                            .chain(classification -> {
                                                if (classification != null) {
                                                    return classification.expire(dbSession, Duration.of(2, HOURS), identityToken);
                                                } else {
                                                    return Uni.createFrom().voidItem();
                                                }
                                            });
                                })
                                .chain(() -> {
                                    return newIp.findClassification(dbSession, SecurityPasswordSalt, system, identityToken)
                                            .chain(classification -> {
                                                if (classification != null) {
                                                    return classification.expire(dbSession, Duration.of(2, HOURS), identityToken);
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
                                                    (String)null,
                                                    confirmationKeyDTO.getConfirmationKey() + "",
                                                    system,
                                                    identityToken)
                                            .chain(() -> {
                                                // Find and expire confirmation key
                                                return newIp.findClassification(dbSession, ConfirmationKey, system, identityToken)
                                                        .chain(classification -> {
                                                            if (classification != null) {
                                                                return classification.expire(dbSession, Duration.of(2, HOURS), identityToken)
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
     * Stateless twin of {@link #createDeviceIP(Mutiny.StatelessSession, ProfileServiceDTO, ISystems, UUID...)} —
     * resolves or creates the device (guest/visitor) involved party for the web-client UUID on a
     * {@link Mutiny.StatelessSession}.
     */
    Uni<IInvolvedParty<?, ?>> createDeviceIP(Mutiny.StatelessSession session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) {
        log.log(Level.FINE, "Creating device IP (stateless) for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
        String webClientUUID = profileServiceDTO.getWebClientUUID().toString();

        return (Uni) withSession(session, dbSession ->
                involvedPartyService.findType(dbSession, TypeDevice.toString(), system, identityToken)
                        .chain(deviceType ->
                                involvedPartyService.get().builder(dbSession)
                                        .findByTypeAll(TypeDevice.toString(), webClientUUID, system, identityToken)
                                        .latestFirst()
                                        .setMaxResults(1)
                                        .get()
                                        // A fresh web-client key has no existing device IP — recover the empty
                                        // result (NoResultException) to null so the create path below runs.
                                        .onFailure(jakarta.persistence.NoResultException.class).recoverWithNull()
                                        .onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
                                        .onItem().ifNull().switchTo(() -> {
                                            log.log(Level.FINE, "Device IP not found, creating new one (stateless)");
                                            Pair<String, String> deviceIDType = new Pair<>();
                                            deviceIDType.setKey(IdentificationTypeWebClientUUID.toString())
                                                    .setValue(webClientUUID);

                                            return (Uni) involvedPartyService.create(dbSession, system, deviceIDType, false, identityToken)
                                                    .chain(newIp -> newIp.addOrReuseInvolvedPartyType(
                                                                    dbSession, NoClassification.toString(),
                                                                    deviceType,
                                                                    webClientUUID,
                                                                    system,
                                                                    identityToken)
                                                            .chain(() -> Uni.createFrom().item(newIp)));
                                        }))
                        .onFailure().invoke(error -> log.log(Level.SEVERE, "Error creating device IP (stateless): {0}", String.valueOf(error))));
    }

    /**
     * Stateless twin of {@link #updateLatestVisit(Mutiny.StatelessSession, IInvolvedParty, ISystems, UUID...)} —
     * stamps the last-visit classification on a {@link Mutiny.StatelessSession}.
     */
    Uni<IInvolvedParty<?, ?>> updateLatestVisit(Mutiny.StatelessSession session, IInvolvedParty<?, ?> newIp, ISystems<?, ?> system,
                                                java.util.UUID... identityToken) {
        log.log(Level.FINE, "Updating last visit time (stateless) for involved party: {0}", newIp.getId());
        String lastVisit = convertToUTCDateTime(com.entityassist.RootEntity.getNow()).format(DateTimeFormatter.ISO_DATE);

        return (Uni) withSession(session, dbSession ->
                newIp.addOrUpdateClassification(dbSession, LastVisitTime,
                                (String) null,
                                lastVisit,
                                system,
                                identityToken)
                        .chain(() -> Uni.createFrom().item(newIp))
                        .onFailure().invoke(error -> log.log(Level.SEVERE, "Error updating last visit time (stateless): {0}", String.valueOf(error))));
    }

    @Override
    public Uni<Boolean> verifyUsernameExists(Mutiny.StatelessSession session, UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Verifying if username exists: {0}", userLoginDTO.getUserName());
        if (Strings.isNullOrEmpty(userLoginDTO.getUserName())) {
            return Uni.createFrom().failure(new ProfileServiceException("Username cannot be empty"));
        }
        return passwordsService.doesUsernameExist(session, userLoginDTO.getUserName(), system, identityToken)
                .onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying username: {0}", String.valueOf(error)));
    }

    @Override
    public Uni<UserLoginDTO<?>> verifyPasswordForUser(Mutiny.StatelessSession session, UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, ISystems<?, ?> system, java.util.UUID... identityToken) {
        log.log(Level.FINE, "Verifying password for user: {0}", userLoginDTO.getUserName());

        // Validate input parameters
        if (Objects.isNull(userLoginDTO.getIdentityToken())) {
            return Uni.createFrom().failure(new ProfileServiceException("User Login DTO Already needs to have an associated UUID to login with a password"));
        }
        if (Strings.isNullOrEmpty(userLoginDTO.getPassword())) {
            return Uni.createFrom().failure(new ProfileServiceException("Passwords cannot be empty"));
        }

        // Operate directly on the caller-provided session (this service never opens its own session/tx)
        return (Uni) withSession(session, dbSession -> {
            // Find involved party by username and password
            return passwordsService.findByUsernameAndPassword(
                            dbSession, userLoginDTO.getUserName(),
                            userLoginDTO.getPassword(),
                            system,
                            true,
                            identityToken)
                    .map(ip -> {
                        // Create and return new UserLoginDTO with identity token
                        return new UserLoginDTO<>().setIdentityToken(ip.getId());
                    })
                    .onFailure().invoke(error -> log.log(Level.SEVERE, "Error verifying password: {0}", String.valueOf(error)));
        });
    }

    /**
     * Stateless twin of {@link #withSession(Mutiny.StatelessSession, java.util.function.Function)} — executes the
     * function on the caller-provided {@link Mutiny.StatelessSession} established by
     * {@code SessionUtils.withActivityMasterStateless(...)} (or {@code withSystemAndTokenStateless}).
     */
    private static <T> Uni<T> withSession(Mutiny.StatelessSession session, java.util.function.Function<Mutiny.StatelessSession, Uni<T>> function) {
        return function.apply(session);
    }
}
