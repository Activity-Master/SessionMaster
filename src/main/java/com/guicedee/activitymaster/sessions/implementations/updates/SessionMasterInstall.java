package com.guicedee.activitymaster.sessions.implementations.updates;

import com.guicedee.activitymaster.fsdm.client.services.IClassificationService;
import com.guicedee.activitymaster.fsdm.client.services.IEventService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.systems.ISystemUpdate;
import com.guicedee.activitymaster.fsdm.client.services.systems.SortedUpdate;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.activitymaster.sessions.services.classifications.SessionEventTypes;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionEventTypes.*;
import static com.guicedee.client.IGuiceContext.*;

/**
 * Installs the default classifications and event types for the Session Master system.
 * This class has been migrated to use reactive patterns with Mutiny's Uni.
 */
@SortedUpdate(sortOrder = 75, taskCount = 1)
public class SessionMasterInstall implements ISystemUpdate
{
    @Override
    public Uni<Boolean> update(Mutiny.Session session, IEnterprise<?,?> enterprise)
    {
        IClassificationService<?> classificationService = get(IClassificationService.class);
        IEventService<?> eventsService = get(IEventService.class);
        SessionMasterSystem systemM = get(SessionMasterSystem.class);

        logProgress("Session Master", "Loading Default Session Classifications");
        
        // Get the system and token first
        return systemM.getSystem(session, enterprise)
            .chain(system -> {
                // Create session classifications
                return classificationService.create(session, SessionInformation, system)
                    .chain(() -> classificationService.create(session, SessionLastUpdateTime, system, SessionInformation))
                    .chain(() -> classificationService.create(session, SessionClassifications.SessionObject, system, SessionInformation))
                    .chain(() -> classificationService.create(session, SystemPerformed, system, SessionInformation))
                    .chain(() -> classificationService.create(session, SessionClassifications.UserLoggedIn, system, SessionInformation))
                    .chain(() -> classificationService.create(session, SessionClassifications.UserLoggedOut, system, SessionInformation))
                    .chain(() -> classificationService.create(session, SessionClassifications.UserSessionExpired, system, SessionInformation))
                    .chain(() -> classificationService.create(session, DeviceUsedBy, system, SessionInformation))
                    .chain(() -> classificationService.create(session, LogonDetails, system, SessionInformation))
                    .chain(() -> classificationService.create(session, LastLoginTime, system, LogonDetails))
                    .chain(() -> classificationService.create(session, LastVisitTime, system, LogonDetails))
                    .chain(() -> classificationService.create(session, ConfirmationKey, system, LogonDetails))
                    .chain(() -> classificationService.create(session, UserRoles, system, LogonDetails))
                    .chain(() -> classificationService.create(session, RememberMe, system, LogonDetails))
                    .chain(() -> classificationService.create(session, LoggedOn, system, LogonDetails))
                    .chain(() -> {
                        // Get system token for event types
                        return systemM.getSystemToken(session, enterprise)
                            .chain(token -> {
                                // Create event types
                                return eventsService.createEventType(session, SiteVisit, system, token)
                                    .chain(() -> eventsService.createEventType(session, UserConfirmedAccount, system, token))
                                    .chain(() -> eventsService.createEventType(session, SessionEventTypes.UserLoggedIn, system, token))
                                    .chain(() -> eventsService.createEventType(session, SessionEventTypes.UserLoggedOut, system, token))
                                    .chain(() -> eventsService.createEventType(session, UserConfirmedAccount, system, token))
                                    .map(result -> true); // Return true to indicate success
                            });
                    });
            });
    }
}
