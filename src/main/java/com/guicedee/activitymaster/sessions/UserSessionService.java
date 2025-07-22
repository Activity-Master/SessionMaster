package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.guicedee.activitymaster.fsdm.client.services.IRelationshipValue;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.ReactiveTransactionUtil;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceData;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.ResourceItemException;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import lombok.extern.java.Log;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

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
    public Uni<IUserSession<?>> getSession(IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        return getSession(involvedParty, new UserSession(), system, identityToken);
    }

    @Override
    public Uni<IUserSession<?>> getSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        if (session == null && involvedParty == null) {
            log.finer("Session has no involved party. First session call?");
            return Uni.createFrom().item(session);
        }

        return ReactiveTransactionUtil.withTransaction(tx -> {
            // Find resource item
            return involvedParty.findResourceItem(SessionClassifications.SessionObject.toString(), 
                    null, system, false, false, identityToken)
                .chain(resourceItem -> {
                    if (resourceItem == null) {
                        // Create new session resource item
                        try {
                            String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                            return createNewSessionResourceItem(involvedParty, system, sessionString, resourceItemService, identityToken, session);
                        } catch (Exception e) {
                            log.log(Level.SEVERE, "Error serializing session", e);
                            return Uni.createFrom().failure(e);
                        }
                    } else {
                        // Use existing resource item
                        IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) resourceItem.getSecondary();
                        return secondary.getDataRow(identityToken)
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
                });
        }).chain(result -> {
            if (result != null) {
                return result.setInvolvedParty(involvedParty);
            }
            return Uni.createFrom().item(result);
        });
    }

    private Uni<IUserSession<?>> createNewSessionResourceItem(
            IInvolvedParty<?, ?> involvedParty, 
            ISystems<?, ?> system, 
            String sessionString, 
            IResourceItemService<?> resourceItemService, 
            java.util.UUID[] identityToken,
            IUserSession<?> session)
    {
        return resourceItemService.create(JsonPacket.toString(), "application/json", sessionString.getBytes(), system, identityToken)
                .chain(ri -> {
                    return ReactiveTransactionUtil.withTransaction(tx -> {
                        try {
                            return involvedParty.addResourceItem(SessionClassifications.SessionObject.toString(), ri, "", system, identityToken)
                                .chain(relationshipValue -> {
                                    IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) relationshipValue.getSecondary();
                                    return secondary.getDataRow(identityToken)
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
                });
    }

    @Override
    public Uni<IUserSession<?>> updateCache(IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        return Uni.createFrom().item(original);
    }

    @Override
    public Uni<Void> removeCache(IInvolvedParty<?, ?> involvedParty)
    {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<IUserSession<?>> expireSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        if (original == null && involvedParty == null) {
            log.finer("Session has no involved party. First session call?");
            return Uni.createFrom().item(original);
        }
        
        IResourceItemService<?> resourceItemService = get(IResourceItemService.class);
        
        return resourceItemService.findByUUID(original.getResourceItemID())
                .chain(resourceItem -> {
                    if (resourceItem != null) {
                        return ReactiveTransactionUtil.withTransaction(session -> {
                            resourceItem.expire();
                            return resourceItem.getDataRow()
                                    .map(data -> original);
                        });
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
    public Uni<IUserSession<?>> updateSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
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
            
            return resourceItemService.findByUUID(session.getResourceItemID())
                    .chain(resourceItem -> {
                        if (resourceItem != null) {
                            return ReactiveTransactionUtil.withTransaction(tx -> {
                                return resourceItem.getDataRow()
                                        .map(dataRow -> {
                                            if (dataRow != null) {
                                                resourceItem.updateData(sessionString.getBytes(), system, identityToken);
                                                return session;
                                            } else {
                                                throw new ResourceItemException("Cannot update a session that has no active data row attached");
                                            }
                                        });
                            });
                        } else {
                            if (session.getResourceItemID() == null && session.getDataID() == null && session.getInvolvedParty() != null) {
                                return getSession(involvedParty, session, system, identityToken);
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