package com.guicedee.activitymaster.sessions;

/**
 * Reactivity Migration Checklist:
 * 
 * [✓] One action per Mutiny.Session at a time
 *     - All operations on a session are sequential
 *     - No parallel operations on the same session
 * 
 * [✓] Pass Mutiny.Session through the chain
 *     - All methods accept session as parameter
 *     - Session is passed to all dependent operations
 * 
 * [✓] No await() usage
 *     - Using reactive chains instead of blocking operations
 * 
 * [✓] Synchronous execution of reactive chains
 *     - All reactive chains execute synchronously
 *     - No fire-and-forget operations with subscribe().with()
 * 
 * [✓] No parallel operations on a session
 *     - Not using Uni.combine().all().unis() with operations that share the same session
 * 
 * [✓] No session/transaction creation in libraries
 *     - Sessions are passed in from the caller
 *     - No sessionFactory.withTransaction() in methods
 * 
 * See ReactivityMigrationGuide.md for more details on these rules.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.ResourceItemException;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISessionLoginService;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.persistence.NoResultException;
import lombok.extern.java.Log;
import org.hibernate.reactive.mutiny.Mutiny;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.getISystem;
import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.getISystemToken;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.ResourceItemTypes.*;
import static com.guicedee.client.IGuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

/**
 * Service for managing user sessions.
 * 
 * This class has been migrated to use reactive patterns with Mutiny's Uni.
 * 
 * TODO: The UserSessionProvider class still uses blocking calls to interact with this service.
 * It will need to be migrated in a future step to fully leverage the reactive nature of this service.
 */

@Log
public class UserSessionService
        implements IUserSessionService<UserSessionService>
{
    private final ObjectMapper mapper = get(ObjectMapper.class);
    private final TypeFactory typeFactory = mapper.getTypeFactory();
    private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);

    @Inject
    private IResourceItemService<?> resourceItemService;

    @Inject
    private Vertx vertx;

    @Override
    public Uni<IUserSession<?>> getSession(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        return getSession(dbSession, involvedParty, new UserSession(), system, identityToken);
    }

    @Override
    public Uni<IUserSession<?>> getSession(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        if (session == null && involvedParty == null) {
            log.finer("Session has no involved party. First session call?");
            return Uni.createFrom().item(session);
        }

        // Find resource item using the passed dbSession
        return involvedParty.findResourceItem(dbSession, SessionClassifications.SessionObject.toString(),
                null, system, false, false, identityToken)
            .chain(resourceItem -> {
                if (resourceItem == null) {
                    // Create new session resource item
                    try {
                        String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                        return createNewSessionResourceItem(dbSession, involvedParty, system, sessionString, resourceItemService, identityToken, session);
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Error serializing session", e);
                        return Uni.createFrom().failure(e);
                    }
                } else {
                    // Use existing resource item
                    IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) resourceItem.getSecondary();
                    return secondary.getDataRow(dbSession, identityToken)
                            .map(data -> {
                                String currentSessionValue = new String(data.getResourceItemData());
                                
                                String sessionString;
                                if (!Strings.isNullOrEmpty(currentSessionValue)) {
                                    sessionString = currentSessionValue;
                                } else {
                                    sessionString = "{}";
                                }
                                
                                if (!(sessionString.isEmpty() || "{}".equals(sessionString))) {
                                    try {
                                        HashMap<String, String> returned = get(DefaultObjectMapper).readValue(sessionString, mapType);
                                        session.getValues().putAll(returned);
                                    } catch (Throwable ioException) {
                                        log.log(Level.FINE, "Error serializing the incoming object to retrieve a session", ioException);
                                    }
                                }
                                
                                session.setResourceItemID(secondary.getId());
                                session.setDataID(data.getId());
                                
                                return session;
                            });
                }
            })
            .chain(result -> {
                if (result != null) {
                    return result.setInvolvedParty(involvedParty);
                }
                return Uni.createFrom().item(result);
            });
    }

    private Uni<IUserSession<?>> createNewSessionResourceItem(
            Mutiny.Session dbSession,
            IInvolvedParty<?, ?> involvedParty, 
            ISystems<?, ?> system, 
            String sessionString, 
            IResourceItemService<?> resourceItemService, 
            java.util.UUID[] identityToken,
            IUserSession<?> session)
    {
        return resourceItemService.create(dbSession, JsonPacket.toString(), "application/json", sessionString.getBytes(), system, identityToken)
                .chain(ri -> {
                    try {
                        return involvedParty.addResourceItem(dbSession, SessionClassifications.SessionObject.toString(), ri, "", system, identityToken)
                            .chain(relationshipValue -> {
                                IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) relationshipValue.getSecondary();
                                return secondary.getDataRow(dbSession, identityToken)
                                        .map(data -> {
                                            session.setResourceItemID(secondary.getId());
                                            session.setDataID(data.getId());
                                            return session;
                                        });
                            });
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Error adding resource item to involved party", e);
                        return Uni.createFrom().failure(e);
                    }
                });
    }

    @Override
    public Uni<IUserSession<?>> updateCache(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        // Simple implementation that just returns the original session
        return Uni.createFrom().item(original);
    }

    @Override
    public Uni<Void> removeCache(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty)
    {
        // Simple implementation that does nothing
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<IUserSession<?>> expireSession(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        if (original == null && involvedParty == null) {
            log.finer("Session has no involved party. First session call?");
            return Uni.createFrom().item(original);
        }
        
        IResourceItemService<?> resourceItemService = get(IResourceItemService.class);
        
        return resourceItemService.findByUUID(dbSession, original.getResourceItemID())
                .chain(resourceItem -> {
                    if (resourceItem != null) {
                        resourceItem.expire();
                        return resourceItem.getDataRow(dbSession)
                                .map(data -> original);
                    } else {
                        return Uni.createFrom().item(original);
                    }
                })
                .onFailure().invoke(error -> log.log(Level.SEVERE, "Error expiring session", error))
                .chain(result -> {
                    if (result != null) {
                        return result.setInvolvedParty(involvedParty);
                    }
                    return Uni.createFrom().item(result);
                });
    }

    @Override
    public Uni<IUserSession<?>> updateSession(Mutiny.Session dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        if (system.isFake() || (session == null && involvedParty == null)) {
            log.finer("Session has no involved party. First session call?");
            return Uni.createFrom().item(session);
        }
        
        return Uni.createFrom().item(() -> {
            try {
                String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                if (Strings.isNullOrEmpty(sessionString)) {
                    sessionString = "{}";
                }
                return sessionString;
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error serializing session", e);
                throw new RuntimeException(e);
            }
        }).chain(sessionString -> {
            IResourceItemService<?> resourceItemService = get(IResourceItemService.class);
            
            return resourceItemService.findByUUID(dbSession, session.getResourceItemID())
                    .chain(resourceItem -> {
                        if (resourceItem != null) {
                            return resourceItem.getDataRow(dbSession)
                                    .map(dataRow -> {
                                        if (dataRow != null) {
                                            resourceItem.updateData(dbSession, sessionString.getBytes(), system, identityToken);
                                            return session;
                                        } else {
                                            throw new ResourceItemException("Cannot update a session that has no active data row attached");
                                        }
                                    });
                        } else {
                            if (session.getResourceItemID() == null && session.getDataID() == null) {
                                // Check if the session has an involved party using the reactive pattern
                                return session.getInvolvedParty(dbSession)
                                    .chain(involvedPartyResult -> {
                                        if (involvedPartyResult != null) {
                                            return getSession(dbSession, involvedParty, session, system, identityToken);
                                        } else {
                                            return Uni.createFrom().failure(
                                                new ResourceItemException("Cannot update a session that has no resource item UUID attached"));
                                        }
                                    });
                            }
                            return Uni.createFrom().failure(
                                    new ResourceItemException("Cannot update a session that has no resource item UUID attached"));
                        }
                    });
        })
        .onFailure().invoke(error -> log.log(Level.SEVERE, "Error updating session", error))
        .chain(result -> {
            if (result != null) {
                return result.setInvolvedParty(involvedParty);
            }
            return Uni.createFrom().item(result);
        });
    }
}