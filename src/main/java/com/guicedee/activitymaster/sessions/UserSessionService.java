package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.guicedee.activitymaster.fsdm.client.services.IRelationshipValue;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceData;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.ResourceItemException;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedpersistence.lambda.TransactionalCallable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.java.Log;

import javax.cache.annotation.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.classifications.ResourceItemTypes.*;
import static com.guicedee.client.IGuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@Log
public class UserSessionService
        implements IUserSessionService<UserSessionService>
{
    private final ObjectMapper mapper = com.guicedee.client.IGuiceContext.get(ObjectMapper.class);
    private final TypeFactory typeFactory = mapper.getTypeFactory();
    private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);

    @Inject
    private IResourceItemService<?> resourceItemService;

    @Inject
    private Vertx vertx;

    @Override
    @CacheResult(cacheName = "SessionsCache")
    public IUserSession<?> getSession(@CacheKey IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        return getSession(involvedParty, new UserSession(), system, identityToken);
    }

    @Override
    @CacheResult(cacheName = "SessionsCache")
    @Transactional()
    public IUserSession<?> getSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        try
        {
            if (session == null && involvedParty == null)
            {
                log.finer("Session has no involved party. First session call?");
                return session;
            }

            Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem = involvedParty.
                    findResourceItem(SessionClassifications.SessionObject.toString(), null, system, false, false, identityToken);

            Promise<IRelationshipValue> promise = Promise.promise();
            Future<IRelationshipValue> future = promise.future();

            if (resourceItem.isEmpty())
            {
                String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                saveNewSessionResourceItem(involvedParty, system, sessionString, resourceItemService, identityToken)
                        .onSuccess(result -> {
                            promise.complete(result);
                        })
                        .onFailure(error -> {
                            log.log(Level.SEVERE, "Error saving new session resource item", error);
                            promise.fail(error);
                            return;
                        });
            }
            else
            {
                promise.complete((IRelationshipValue) resourceItem.get());
            }
            future.onSuccess(res -> {
                IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) res.getSecondary();
                Optional<IResourceData<?, ?, ?>> data = secondary.getDataRow(identityToken);
                String currentSessionValue = new String(data.get()
                        .getResourceItemData());
                String sessionString;
                if (!Strings.isNullOrEmpty(currentSessionValue))
                {
                    sessionString = currentSessionValue;
                }
                else
                {
                    sessionString = "{}";
                }
                HashMap<String, String> returned = new HashMap<>();
                if (!(sessionString.isEmpty() || "{}".equals(sessionString)))
                {
                    try
                    {
                        returned = get(DefaultObjectMapper).readValue(sessionString, mapType);
                        session.getValues()
                                .putAll(returned);
                    }
                    catch (Throwable ioException)
                    {
                        //Guest?
                        log.log(Level.FINE, "Error serializing the incoming object to retrieve a session", ioException);
                    }
                }

                session.setResourceItemID(secondary.getId());
                session.setDataID(data.get().getId());
            });

        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
        }
        if (session != null)
        {
            //session.setSystem(system);
            session.setInvolvedParty(involvedParty);
        }
        return session;
    }

    @Transactional()
    Future<IRelationshipValue<?, IResourceItem<?, ?>, ?>> saveNewSessionResourceItem(IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, String sessionString, IResourceItemService<?> resourceItemService, java.util.UUID[] identityToken) throws TimeoutException
    {
        Promise<IRelationshipValue<?, IResourceItem<?, ?>, ?>> promise = Promise.promise();
        var newResource =
                resourceItemService.create(JsonPacket.toString(), "application/json", sessionString.getBytes(), system, identityToken);

        newResource.onSuccess((ri) -> {
            vertx.executeBlocking(TransactionalCallable.of(() -> {
                var rid = involvedParty.addResourceItem(SessionClassifications.SessionObject.toString(), ri, "", system, identityToken);
                promise.complete(rid);
                return true;
            }, true), true);
        });
        return promise.future();
    }

    @Override
    @CacheResult(cacheName = "SessionsCache", skipGet = true)
    public IUserSession<?> updateCache(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        return original;
    }

    @Override
    @CacheRemove(cacheName = "SessionsCache")
    public void removeCache(@CacheKey IInvolvedParty<?, ?> involvedParty)
    {
    }

    @Override
    @CacheResult(cacheName = "SessionsCache", skipGet = true)
    @Transactional()
    public IUserSession<?> expireSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        IUserSession<?> session = original;
        try
        {
            if (session == null && involvedParty == null)
            {
                log.finer("Session has no involved party. First session call?");
                return session;
            }
            IResourceItemService<?> resourceItemService = com.guicedee.client.IGuiceContext.get(IResourceItemService.class);

            // Since findByUUID now returns a Future, we need to handle it differently
            // This is a blocking call, which is not ideal but necessary for backward compatibility
            var resourceItemFuture = resourceItemService.findByUUID(original.getResourceItemID());
            IResourceItem<?, ?> resourceItem = resourceItemFuture.toCompletionStage().toCompletableFuture().join();

            if (resourceItem != null) {
                resourceItem.expire();
                resourceItem.getDataRow();
            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
        }
        if (session != null)
        {
            //session.setSystem(system);
            session.setInvolvedParty(involvedParty);
        }
        return session;
    }

    @Override
    @CacheResult(cacheName = "SessionsCache", skipGet = true)
    @Transactional()
    public IUserSession<?> updateSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
    {
        try
        {
            if (system.isFake() || (session == null && involvedParty == null))
            {
                log.finer("Session has no involved party. First session call?");
                return session;
            }
            String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
            if (Strings.isNullOrEmpty(sessionString))
            {
                sessionString = "{}";
            }
            IResourceItemService<?> resourceItemService = com.guicedee.client.IGuiceContext.get(IResourceItemService.class);

            // Since findByUUID now returns a Future, we need to handle it differently
            // This is a blocking call, which is not ideal but necessary for backward compatibility
            var resourceItemFuture = resourceItemService.findByUUID(session.getResourceItemID());
            IResourceItem<?, ?> resourceItem = resourceItemFuture.toCompletionStage().toCompletableFuture().join();

            if (resourceItem != null)
            {
                var dataRow = resourceItem.getDataRow();
                if (dataRow.isPresent())
                {
                    resourceItem.updateData(sessionString.getBytes(), system, identityToken);
                }
                else
                {
                    throw new ResourceItemException("Cannot update a session that has no active data row attached");
                }
            }
            else
            {
                if (session.getResourceItemID() == null && session.getDataID() == null && session.getInvolvedParty() != null)
                {
                    return getSession(involvedParty, session, system, identityToken);
                }
                throw new ResourceItemException("Cannot update a session that has no resource item UUID attached");
            }
        }
        catch (IOException e)
        {
            log.log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
        }
        if (session != null)
        {
         //   session.setSystem(system);
            session.setInvolvedParty(involvedParty);
        }
        return session;
    }


}
