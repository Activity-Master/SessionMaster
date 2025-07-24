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
import io.vertx.core.Future;
import org.hibernate.reactive.mutiny.Mutiny;

import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionEventTypes.*;
import static com.guicedee.client.IGuiceContext.*;

@SortedUpdate(sortOrder = 75, taskCount = 1)
public class SessionMasterInstall implements ISystemUpdate
{
	@Override
	public Future<Boolean> update(Mutiny.Session session, IEnterprise<?,?> enterprise)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);

		logProgress("Session Master", "Loading Default Session Classifications");
		SessionMasterSystem systemM = get(SessionMasterSystem.class);
		ISystems<?,?> system = systemM.getSystem(session, enterprise);

		classificationService.create(session, SessionInformation, system);

		classificationService.create(session, SessionLastUpdateTime, system,SessionInformation);
		classificationService.create(session, SessionClassifications.SessionObject, system,SessionInformation);


		classificationService.create(session, SystemPerformed, system,SessionInformation);
		classificationService.create(session, SessionClassifications.UserLoggedIn, system,SessionInformation);
		classificationService.create(session, SessionClassifications.UserLoggedOut, system,SessionInformation);
		classificationService.create(session, SessionClassifications.UserSessionExpired, system,SessionInformation);



		IEventService<?> eventsService = get(IEventService.class);

		eventsService.createEventType(session, SiteVisit, system, systemM.getSystemToken(session, enterprise));
		eventsService.createEventType(session, UserConfirmedAccount, system, systemM.getSystemToken(session, enterprise));
		eventsService.createEventType(session, SessionEventTypes.UserLoggedIn, system, systemM.getSystemToken(session, enterprise));
		eventsService.createEventType(session, SessionEventTypes.UserLoggedOut, system, systemM.getSystemToken(session, enterprise));
		eventsService.createEventType(session, UserConfirmedAccount, system, systemM.getSystemToken(session, enterprise));

		classificationService.create(session, DeviceUsedBy, system,SessionInformation);


		classificationService.create(session, LogonDetails, system,SessionInformation);
		classificationService.create(session, LastLoginTime, system, LogonDetails);
		classificationService.create(session, LastVisitTime, system, LogonDetails);
		classificationService.create(session, ConfirmationKey, system, LogonDetails);
		classificationService.create(session, UserRoles, system, LogonDetails);
		classificationService.create(session, RememberMe, system, LogonDetails);
		classificationService.create(session, LoggedOn, system, LogonDetails);

		return Future.succeededFuture(true);
	}

}
