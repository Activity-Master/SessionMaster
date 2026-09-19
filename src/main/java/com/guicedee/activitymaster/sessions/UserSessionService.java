package com.guicedee.activitymaster.sessions;

/**
 * Reactivity Migration Checklist:
 * <p>
 * [✓] One action per Mutiny.StatelessSession at a time
 * - All operations on a session are sequential
 * - No parallel operations on the same session
 * <p>
 * [✓] Pass Mutiny.StatelessSession through the chain
 * - All methods accept session as parameter
 * - Session is passed to all dependent operations
 * <p>
 * [✓] No await() usage
 * - Using reactive chains instead of blocking operations
 * <p>
 * [✓] Synchronous execution of reactive chains
 * - All reactive chains execute synchronously
 * - No fire-and-forget operations with subscribe().with()
 * <p>
 * [✓] No parallel operations on a session
 * - Not using Uni.combine().all().unis() with operations that share the same session
 * <p>
 * [✓] No session/transaction creation in libraries
 * - Sessions are passed in from the caller
 * - No sessionFactory.withTransaction() in methods
 * <p>
 * See ReactivityMigrationGuide.md for more details on these rules.
 */

import io.smallrye.mutiny.unchecked.Unchecked;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.MapType;
import tools.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import lombok.extern.java.Log;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.HashMap;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.classifications.ResourceItemTypes.JsonPacket;
import static com.guicedee.client.IGuiceContext.get;
import static com.guicedee.client.implementations.ObjectBinderKeys.DefaultObjectMapper;

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
        implements IUserSessionService<UserSessionService> {
    private final ObjectMapper mapper = get(ObjectMapper.class);
    private final TypeFactory typeFactory = mapper.getTypeFactory();
    private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);

    @Inject
    private IResourceItemService<?> resourceItemService;

    @Inject
    private Vertx vertx;

    // ---- Stateless twins ----

    @Override
    public Uni<IUserSession<?>> getUserSession(Mutiny.StatelessSession dbSession, IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken) {
        return getUserSession(dbSession, involvedParty, new UserSession(), system, identityToken);
    }

    @Override
    public Uni<IUserSession<?>> getUserSession(Mutiny.StatelessSession dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken) {
        if (session == null && involvedParty == null) {
            return Uni.createFrom().item(session);
        }
        return involvedParty
                .findResourceItem(dbSession,
                                  SessionClassifications.SessionObject.toString(),
                                  null,
                                  system,
                                  false,
                                  false,
                                  identityToken
                )
                // First-time session has no SessionObject resource item — recover the empty-result
                // NoResultException to null to drive the "create new session resource item" branch.
                .onFailure(jakarta.persistence.NoResultException.class).recoverWithNull()
                .chain(resourceItem -> {
                    if (resourceItem == null) {
                        try {
                            String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                            return createNewSessionResourceItem(dbSession,
                                                                involvedParty,
                                                                system,
                                                                sessionString,
                                                                resourceItemService,
                                                                identityToken,
                                                                session
                            );
                        } catch (Exception e) {
                            log.log(Level.SEVERE, "Error serializing session", e);
                            return Uni.createFrom().failure(e);
                        }
                    }
                    IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) resourceItem.getSecondary();
                    return secondary.getData(dbSession, identityToken)
                            .chain(data -> {
                                String currentSessionValue = new String(data);
                                String sessionString = Strings.isNullOrEmpty(currentSessionValue) ? "{}" : currentSessionValue;
                                if (!("{}".equals(sessionString))) {
                                    try {
                                        HashMap<String, String> returned = get(DefaultObjectMapper).readValue(
                                                sessionString,
                                                mapType
                                        );
                                        session.getValues().putAll(returned);
                                    } catch (Throwable ioException) {
                                        log.log(Level.FINE, "Error reading incoming session", ioException);
                                    }
                                }
                                session.setResourceItemID(secondary.getId());
                                session.setDataID(secondary.getId());
                                return Uni.createFrom().item(session);
                            });
                });
    }

    private Uni<IUserSession<?>> createNewSessionResourceItem(
            Mutiny.StatelessSession dbSession,
            IInvolvedParty<?, ?> involvedParty,
            ISystems<?, ?> system,
            String sessionString,
            IResourceItemService<?> resourceItemService,
            java.util.UUID[] identityToken,
            IUserSession<?> session
    ) {
        return resourceItemService
                .create(dbSession,
                        JsonPacket.toString(),
                        "application/json",
                        sessionString.getBytes(),
                        system,
                        identityToken
                )
                .chain(ri -> involvedParty
                        .addResourceItem(dbSession,
                                         SessionClassifications.SessionObject.toString(),
                                         ri,
                                         "",
                                         system,
                                         identityToken
                        )
                        .chain(relationshipValue -> {
                            IResourceItem<?, ?> secondary = (IResourceItem<?, ?>) relationshipValue.getSecondary();
                            return secondary.getDataRow(dbSession, identityToken).map(data -> {
                                session.setResourceItemID(secondary.getId());
                                session.setDataID(data.getId());
                                return session;
                            });
                        }));
    }

    @Override
    public Uni<IUserSession<?>> expireSession(Mutiny.StatelessSession dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken) {
        if (original == null && involvedParty == null) {
            return Uni.createFrom().item(original);
        }
        return resourceItemService.findByUUID(dbSession, original.getResourceItemID())
                .chain(resourceItem -> {
                    if (resourceItem != null) {
                        return resourceItem.expire(dbSession)
                                .chain(expired -> expired.getDataRow(dbSession).map(data -> original));
                    }
                    return Uni.createFrom().item(original);
                })
                .onFailure().invoke(error -> log.log(Level.SEVERE, "Error expiring session (stateless)", error));
    }

    @Override
    public Uni<IUserSession<?>> updateSession(Mutiny.StatelessSession dbSession, IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken) {
        if (system.isFake() || (session == null && involvedParty == null)) {
            return Uni.createFrom().item(session);
        }
        return Uni.createFrom().item(Unchecked.supplier(() -> {
            try {
                String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
                return Strings.isNullOrEmpty(sessionString) ? "{}" : sessionString;
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error serializing session", e);
                throw new RuntimeException(e);
            }
        })).chain(sessionString -> {
            return resourceItemService.updateResourceData(dbSession,
                                                          sessionString.getBytes(),
                                                          session.getResourceItemID()
            ).replaceWith(dbSession);
        }).map(a -> (IUserSession<?>) a);
    }
}