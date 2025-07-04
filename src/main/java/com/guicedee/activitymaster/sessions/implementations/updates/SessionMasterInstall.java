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

import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionEventTypes.*;
import static com.guicedee.client.IGuiceContext.*;

@SortedUpdate(sortOrder = 75, taskCount = 1)
public class SessionMasterInstall implements ISystemUpdate
{
	@Override
	public Future<Boolean> update(IEnterprise<?,?> enterprise)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);

		logProgress("Session Master", "Loading Default Session Classifications");
		SessionMasterSystem systemM = get(SessionMasterSystem.class);
		ISystems<?,?> system = systemM.getSystem(enterprise);

		classificationService.create(SessionInformation, system);

		classificationService.create(SessionLastUpdateTime, system,SessionInformation);
		classificationService.create(SessionClassifications.SessionObject, system,SessionInformation);


		classificationService.create(SystemPerformed, system,SessionInformation);
		classificationService.create(SessionClassifications.UserLoggedIn, system,SessionInformation);
		classificationService.create(SessionClassifications.UserLoggedOut, system,SessionInformation);
		classificationService.create(SessionClassifications.UserSessionExpired, system,SessionInformation);



		IEventService<?> eventsService = get(IEventService.class);

		eventsService.createEventType(SiteVisit, system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(UserConfirmedAccount, system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(SessionEventTypes.UserLoggedIn, system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(SessionEventTypes.UserLoggedOut, system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(UserConfirmedAccount, system, systemM.getSystemToken(enterprise));

		classificationService.create(DeviceUsedBy, system,SessionInformation);


		classificationService.create(LogonDetails, system,SessionInformation);
		classificationService.create(LastLoginTime, system, LogonDetails);
		classificationService.create(LastVisitTime, system, LogonDetails);
		classificationService.create(ConfirmationKey, system, LogonDetails);
		classificationService.create(UserRoles, system, LogonDetails);
		classificationService.create(RememberMe, system, LogonDetails);
		classificationService.create(LoggedOn, system, LogonDetails);

		return Future.succeededFuture(true);
	}

}
