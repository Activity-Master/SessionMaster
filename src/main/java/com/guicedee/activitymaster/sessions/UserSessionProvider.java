package com.guicedee.activitymaster.sessions;

/**
 * Reactivity Migration Checklist:
 * <p>
 * [✓] One action per Mutiny.Session at a time
 * - All operations on a session are sequential
 * - No parallel operations on the same session
 * <p>
 * [!] Pass Mutiny.Session through the chain
 * - Creates a session using sessionFactory.withSession() but doesn't pass it through a chain
 * - Uses the session only for a single operation
 * <p>
 * [!] No await() usage
 * - Uses .await().indefinitely() multiple times
 * - This is explicitly noted as a temporary solution for backward compatibility
 * <p>
 * [!] Synchronous execution of reactive chains
 * - Doesn't use proper reactive chains
 * - Blocks on reactive operations using .await().indefinitely()
 * <p>
 * [✓] No parallel operations on a session
 * - Not using Uni.combine().all().unis() with operations that share the same session
 * <p>
 * [!] No session/transaction creation in libraries
 * - Creates a session using sessionFactory.withSession()
 * - Should accept a session parameter instead
 * <p>
 * See ReactivityMigrationGuide.md for more details on these rules.
 * <p>
 * NOTE: This class is explicitly marked as needing migration to reactive patterns in the future.
 * The comments acknowledge many of the issues identified in this checklist.
 */

import com.google.inject.*;
import com.google.inject.name.Names;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.*;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import lombok.extern.java.Log;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.*;
import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

/**
 * Provider for UserSession instances.
 * <p>
 * TODO: This class needs to be migrated to use reactive patterns in a future migration step.
 * Currently, it uses blocking calls to handle the reactive UserSessionService, which is not ideal
 * but necessary for backward compatibility during the migration process.
 * <p>
 * This is a temporary solution until the full migration is complete. The UserSessionService has
 * been migrated to use reactive patterns, but this provider still uses the old approach.
 * <p>
 * Progress on migration:
 * 1. The getISystem and getISystemToken methods have been updated to include the enterprise parameter
 * and properly handle the Uni return types.
 * 2. The blocking builder().find() approach has been replaced with the reactive find() method,
 * though it still uses .await().indefinitely() for backward compatibility.
 * <p>
 * Future work:
 * - The entire get() method should be refactored to return a Uni<IUserSession<UserSession>> instead
 * of a blocking IUserSession<UserSession> to fully comply with reactive patterns.
 * - Fix compilation errors related to the reactive nature of the methods being called.
 * - Update the find() method call to use the correct parameters.
 * - Handle the Uni return type from loginVisitor() method.
 * <p>
 * IMPORTANT: This class currently has compilation errors that need to be addressed in a separate task.
 * The focus of the current migration was to create the ReactiveTransactionUtil class and update
 * the SessionMasterSystem to use reactive patterns.
 */
@Log
public class UserSessionProvider
    implements Provider<IUserSession<UserSession>>
{
  @Inject
  private IUserSessionService<?> sessionMasterService;

  @Inject
  private IInvolvedPartyService<?> involvedPartyService;

  @Inject
  private IEnterprise<?, ?> enterprise;

  @Inject
  private Mutiny.SessionFactory sessionFactory;

  /**
   * Gets a user session.
   * <p>
   * NOTE: This implementation uses blocking calls to handle the reactive UserSessionService.
   * This is not ideal but necessary for backward compatibility during the migration process.
   * In the future, this class should be migrated to use reactive patterns.
   */
  @Override
  public IUserSession<UserSession> get()
  {
    if (sessionMasterService == null)
    {
      com.guicedee.client.IGuiceContext.instance()
          .inject()
          .injectMembers(this)
      ;
    }
    if (enterprise.isFake())
    {
      return null;
    }

    UUID localStorageKey = com.guicedee.client.IGuiceContext.get(Key.get(UUID.class, Names.named("localstorage")));
    if (localStorageKey == null)
    {
      //this call does not have any local storage attached? Perhaps from a web call?
      return null;
    }
    else
    {
      try
      {
        // Using the reactive find method instead of the blocking builder().find() approach
        // This is still using .await().indefinitely() for backward compatibility,
        // but it's a step towards making this code fully reactive in the future.
        // Use sessionFactory to create a session for the find operation
        IInvolvedParty<?, ?> byUUID = sessionFactory.withSession(session ->
                                                                     session.withTransaction(tx -> {
                                                                       return involvedPartyService.find(session, localStorageKey)
                                                                                  .onFailure(NoResultException.class)
                                                                                  .recoverWithUni(a -> {
                                                                                    ISessionLoginService<?> sessionLoginService = com.guicedee.client.IGuiceContext.get(ISessionLoginService.class);
                                                                                    ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
                                                                                    // Get system and token using the enterprise parameter and await the results
                                                                                    ISystems<?, ?> system = getISystem(SessionMasterSystemName, enterprise).await()
                                                                                                                .indefinitely()
                                                                                        ;
                                                                                    UUID systemToken = getISystemToken(SessionMasterSystemName, enterprise).await()
                                                                                                           .indefinitely()
                                                                                        ;

                                                                                    // Handle the Uni return type from loginVisitor properly

                                                                                    return sessionLoginService.loginVisitor(session, dto, system, systemToken)
                                                                                               .chain(dtoInner->{
                                                                                                 return dtoInner.findInvolvedPartyReactive();
                                                                                               });

                                                                                  });
                                                                     })

            )
                                          .await()
                                          .indefinitely()
            ;


        // NOTE: This is a blocking call to a reactive method, which is not ideal but necessary for backward compatibility.
        // In a future migration step, this should be updated to use reactive patterns.
        //noinspection unchecked
        // Get system and token using the enterprise parameter and await the results
        ISystems<?, ?> system = getISystem(SessionMasterSystemName, enterprise).await()
                                    .indefinitely()
            ;
        UUID systemToken = getISystemToken(SessionMasterSystemName, enterprise).await()
                               .indefinitely()
            ;

        // Create final copies of variables for use in lambda expression
        final IInvolvedParty<?, ?> finalByUUID = byUUID;
        final ISystems<?, ?> finalSystem = system;
        final UUID finalSystemToken = systemToken;

        // Use sessionFactory to create a session for the getSession operation
        // and pass the session as the first parameter
        return (IUserSession<UserSession>) sessionFactory.withSession(session ->
                                                                          sessionMasterService.getSession(session, finalByUUID, finalSystem, finalSystemToken)
            )
                                               .await()
                                               .indefinitely();
      }
      catch (Throwable T)
      {
        log.log(Level.SEVERE, "Cannot work on session", T);
        return new UserSession();
      }
    }
  }
}