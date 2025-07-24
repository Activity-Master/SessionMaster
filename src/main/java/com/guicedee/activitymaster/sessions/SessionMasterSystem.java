package com.guicedee.activitymaster.sessions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.guicedee.activitymaster.fsdm.client.services.ISystemsService;
import com.guicedee.activitymaster.fsdm.client.services.administration.ActivityMasterDefaultSystem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.systems.IActivityMasterSystem;
import io.smallrye.mutiny.Uni;
import lombok.extern.java.Log;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.logging.Level;

import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

/**
 * SessionMasterSystem is responsible for registering the SessionMaster system with the enterprise
 * and providing system information.
 * 
 * This class has been migrated to use reactive patterns with Mutiny's Uni.
 * It uses a hybrid approach to maintain backward compatibility with the IActivityMasterSystem interface:
 * - Original methods required by the interface are kept with their original signatures
 * - New reactive methods are added with "Reactive" suffix
 * - Original methods call the reactive versions using .await().indefinitely()
 * 
 * This approach allows for a gradual migration to reactive programming while maintaining
 * compatibility with existing code.
 */
@Log
public class SessionMasterSystem
		extends ActivityMasterDefaultSystem<SessionMasterSystem>
		implements IActivityMasterSystem<SessionMasterSystem>
{
	@Inject
	private Provider<ISystemsService<?>> systemsService;
	
	/**
	 * Registers the SessionMaster system with the enterprise.
	 * This method maintains backward compatibility by calling the reactive version
	 * and blocking until the result is available.
	 *
	 * @param session
	 * @param enterprise The enterprise to register the system with
	 * @return The registered system
	 */
	@Override
	public ISystems<?,?> registerSystem(Mutiny.Session session, IEnterprise<?,?> enterprise)
	{
		try {
			return registerSystemReactive(enterprise)
					.await()
					.indefinitely();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error registering system: " + e.getMessage(), e);
			throw new RuntimeException("Failed to register system", e);
		}
	}
	
	/**
	 * Reactive version of registerSystem.
	 * Registers the SessionMaster system with the enterprise using reactive patterns.
	 * 
	 * @param enterprise The enterprise to register the system with
	 * @return A Uni that emits the registered system
	 */
	public Uni<ISystems<?,?>> registerSystemReactive(IEnterprise<?,?> enterprise)
	{
		// First create the system
		Uni<ISystems<?,?>> createUni = systemsService.get()
				.create(session, enterprise, getSystemName(), getSystemDescription());
		
		// Return the created system, but also register it
		return createUni
				.onItem().invoke(iSystems -> {
					// Register the system in a fire-and-forget manner
					systemsService.get()
							.registerNewSystem(session, enterprise, getSystem(session, enterprise))
							.subscribe().with(
									result -> log.info("System registered successfully: " + result),
									error -> log.log(Level.SEVERE, "Error registering system: " + error.getMessage(), error)
							);
				})
				.onFailure().invoke(error -> 
					log.log(Level.SEVERE, "Error creating system: " + error.getMessage(), error)
				);
	}
	
	/**
	 * Creates default settings for the system.
	 * This method maintains backward compatibility by calling the reactive version
	 * and blocking until the result is available.
	 *
	 * @param session
	 * @param enterprise The enterprise to create defaults for
	 */
	@Override
	public void createDefaults(Mutiny.Session session, IEnterprise<?,?> enterprise)
	{
		try {
			createDefaultsReactive(enterprise)
					.await()
					.indefinitely();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error creating defaults: " + e.getMessage(), e);
			throw new RuntimeException("Failed to create defaults", e);
		}
	}
	
	/**
	 * Reactive version of createDefaults.
	 * Creates default settings for the system using reactive patterns.
	 * 
	 * @param enterprise The enterprise to create defaults for
	 * @return A Uni that completes when the defaults are created
	 */
	public Uni<Void> createDefaultsReactive(IEnterprise<?,?> enterprise)
	{
		// Currently no defaults to create
		return Uni.createFrom().voidItem()
				.onFailure().invoke(error -> 
					log.log(Level.SEVERE, "Error creating defaults: " + error.getMessage(), error)
				);
	}

	@Override
	public int totalTasks()
	{
		return 0;
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