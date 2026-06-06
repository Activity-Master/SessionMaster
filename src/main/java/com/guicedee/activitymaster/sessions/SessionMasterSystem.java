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
 * [!] Synchronous execution of reactive chains
 *     - Most reactive chains execute synchronously
 *     - The registerSystem method uses fire-and-forget operations with subscribe().with()
 *       which should be properly chained instead
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

import com.google.inject.Inject;
import com.guicedee.activitymaster.fsdm.client.services.ISystemsService;
import com.guicedee.activitymaster.fsdm.client.services.administration.MasterDefaultSystem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.systems.IMasterSystem;
import io.smallrye.mutiny.Uni;
import lombok.extern.log4j.Log4j2;
import org.hibernate.reactive.mutiny.Mutiny;

import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

/**
 * SessionMasterSystem is responsible for registering the SessionMaster system with the enterprise
 * and providing system information.
 * 
 * This class follows the reactive patterns used throughout the ActivityMaster project,
 * using Mutiny's Uni for asynchronous operations.
 */
@Log4j2
public class SessionMasterSystem
    extends MasterDefaultSystem<SessionMasterSystem>
    implements IMasterSystem<SessionMasterSystem>
{
    @Inject
    private ISystemsService<?> systemsService;
    
    @Inject
    private Mutiny.SessionFactory sessionFactory;
    
    @Override
    public Uni<ISystems<?, ?>> registerSystem(Mutiny.Session session, IEnterprise<?, ?> enterprise)
    {
        log.info("🚀 Registering Session Master System for enterprise: '{}'", enterprise.getName());
        log.debug("📋 Creating Session Master System with session: {}", session.hashCode());
        
        // First create the system
        return systemsService
                .create(session, enterprise, getSystemName(), getSystemDescription())
                .onItem()
                .invoke(system -> log.debug("✅ Created Session Master System: '{}' with session: {}", system.getName(), session.hashCode()))
                .onFailure()
                .invoke(error -> log.error("❌ Failed to create Session Master System: '{}' with session {}: {}",
                    getSystemName(), session.hashCode(), error.getMessage(), error))
                // Store the created system to return it later
                .call(createdSystem -> {
                    // Perform the registration in a non-blocking way
                    // This ensures we don't block the return of the created system on the registration process
                    // but still maintain proper reactive chaining
                    return getSystem(session, enterprise)
                        .chain(sys -> systemsService.registerNewSystem(session, enterprise, sys))
                        .onItem()
                        .invoke(() -> {
                            log.debug("✅ Registered system: {}", getSystemName());
                            log.info("🎉 Successfully registered Session Master System for enterprise: '{}'", enterprise.getName());
                        })
                        .onFailure()
                        .invoke(error -> log.error("❌ Error registering system: {}", error.getMessage(), error))
                        // Use recoverWithItem to ensure the chain completes even if registration fails
                        .onFailure().recoverWithItem(() -> null);
                });
    }
    
    @Override
    public Uni<Void> createDefaults(Mutiny.Session session, IEnterprise<?, ?> enterprise)
    {
        logProgress("Session Master System", "Starting Session Checks");
        log.info("🚀 Creating session defaults for enterprise: '{}'", enterprise.getName());
        log.debug("📋 Starting with session: {}", session.hashCode());
        
        // No actual operations needed, just return a void item
        log.debug("✅ No specific defaults needed for Session Master System");
        return Uni.createFrom()
                .voidItem()
                .onItem()
                .invoke(() -> log.info("🎉 Successfully completed Session Master System defaults"))
                .onFailure()
                .invoke(error -> log.error("❌ Error in Session Master System defaults: {}", error.getMessage(), error))
                .replaceWithVoid();
    }
    
    @Override
    public Uni<Void> postStartup(Mutiny.Session session, IEnterprise<?, ?> enterprise)
    {
        log.info("🚀 Starting reactive postStartup for Session Master System");
        log.debug("📋 Beginning postStartup operations for enterprise: '{}' with session: {}", 
                enterprise.getName(), session.hashCode());
        
        // Get the system and token first
        return getSystem(session, enterprise)
                .onItem()
                .invoke(system -> log.debug("✅ Found system: '{}'", system.getName()))
                .onItem()
                .ifNull()
                .failWith(() -> new RuntimeException("System not found: " + getSystemName()))
                .onFailure()
                .invoke(error -> log.error("❌ Failed to find system: {}", error.getMessage(), error))
                .chain(system -> {
                    log.debug("🔍 Retrieving security token for system: '{}'", system.getName());
                    return systemsService.getSecurityIdentityToken(session, system)
                            .onItem()
                            .invoke(token -> log.debug("🔑 Found security token for system: '{}'", system.getName()))
                            .onItem()
                            .ifNull()
                            .failWith(() -> new RuntimeException("Security token not found for system: " + system.getName()))
                            .onFailure()
                            .invoke(error -> log.error("❌ Failed to retrieve security token: {}", error.getMessage(), error))
                            .map(token -> {
                                log.debug("✅ Successfully completed postStartup for Session Master System");
                                return null; // Return Void
                            });
                })
                .replaceWith(Uni.createFrom()
                        .voidItem())
                .onItem()
                .invoke(() -> log.info("🎉 Session Master System postStartup completed successfully"))
                .onFailure()
                .invoke(error -> log.error("❌ Error in Session Master System postStartup: {}", error.getMessage(), error));
    }
    
    @Override
    public int totalTasks()
    {
        return 0;
    }
    
    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 20;
    }
    
    @Override
    public String getSystemName()
    {
        return SessionMasterSystemName;
    }
    
    @Override
    public String getSystemDescription()
    {
        return "The system for handling distributed sessions";
    }
}