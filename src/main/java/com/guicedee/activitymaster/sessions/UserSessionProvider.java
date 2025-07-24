package com.guicedee.activitymaster.sessions;

import com.google.inject.*;
import com.google.inject.name.Names;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.*;
import lombok.extern.java.Log;

import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.*;
import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

/**
 * Provider for UserSession instances.
 * 
 * TODO: This class needs to be migrated to use reactive patterns in a future migration step.
 * Currently, it uses blocking calls to handle the reactive UserSessionService, which is not ideal
 * but necessary for backward compatibility during the migration process.
 * 
 * This is a temporary solution until the full migration is complete. The UserSessionService has
 * been migrated to use reactive patterns, but this provider still uses the old approach.
 * 
 * Progress on migration:
 * 1. The getISystem and getISystemToken methods have been updated to include the enterprise parameter
 *    and properly handle the Uni return types.
 * 2. The blocking builder().find() approach has been replaced with the reactive find() method,
 *    though it still uses .await().indefinitely() for backward compatibility.
 * 
 * Future work:
 * - The entire get() method should be refactored to return a Uni<IUserSession<UserSession>> instead
 *   of a blocking IUserSession<UserSession> to fully comply with reactive patterns.
 */
@Log
public class UserSessionProvider
		implements Provider<IUserSession<UserSession>>
{
	@Inject
	private IEnterprise<?, ?> enterprise;
	
	@Inject
	private IUserSessionService<?> sessionMasterService;
	
	@Inject
	private IInvolvedPartyService<?> involvedPartyService;
	
	/**
	 * Gets a user session.
	 * 
	 * NOTE: This implementation uses blocking calls to handle the reactive UserSessionService.
	 * This is not ideal but necessary for backward compatibility during the migration process.
	 * In the future, this class should be migrated to use reactive patterns.
	 */
	@Override
	public IUserSession<UserSession> get()
	{
		if (sessionMasterService == null)
		{
			com.guicedee.client.IGuiceContext.instance().inject()
			            .injectMembers(this);
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
				IInvolvedParty<?, ?> byUUID = involvedPartyService.find(session, localStorageKey)
				                                                  .await()
				                                                  .indefinitely();
				
				if (byUUID == null)
				{
					ISessionLoginService<?> sessionLoginService = com.guicedee.client.IGuiceContext.get(ISessionLoginService.class);
					ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
					// Get system and token using the enterprise parameter and await the results
					ISystems<?, ?> system = getISystem(SessionMasterSystemName, enterprise).await().indefinitely();
					UUID systemToken = getISystemToken(SessionMasterSystemName, enterprise).await().indefinitely();
					
					ProfileServiceDTO<?> profileServiceDTO = sessionLoginService.loginVisitor(dto, system, systemToken);
					byUUID = profileServiceDTO.findInvolvedParty();
				}
				
				// NOTE: This is a blocking call to a reactive method, which is not ideal but necessary for backward compatibility.
				// In a future migration step, this should be updated to use reactive patterns.
				//noinspection unchecked
				// Get system and token using the enterprise parameter and await the results
				ISystems<?, ?> system = getISystem(SessionMasterSystemName, enterprise).await().indefinitely();
				UUID systemToken = getISystemToken(SessionMasterSystemName, enterprise).await().indefinitely();
				
				return (IUserSession<UserSession>) sessionMasterService.getSession(byUUID, system, systemToken)
						.await().indefinitely();
			}
			catch (Throwable T)
			{
				log.log(Level.SEVERE, "Cannot work on session", T);
				return new UserSession();
			}
		}
	}
}