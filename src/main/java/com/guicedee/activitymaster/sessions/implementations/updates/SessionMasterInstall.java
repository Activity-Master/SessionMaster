package com.guicedee.activitymaster.sessions.implementations.updates;

import com.guicedee.activitymaster.client.services.IClassificationService;
import com.guicedee.activitymaster.client.services.IEventService;
import com.guicedee.activitymaster.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.client.services.systems.*;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;

import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileEventTypes.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.guicedinjection.GuiceContext.*;

@SortedUpdate(sortOrder = 75, taskCount = 1)
public class SessionMasterInstall implements ISystemUpdate
{
	@Override
	public void update(IEnterprise<?,?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);
		
		logProgress("Session Master", "Loading Default Session Classifications", progressMonitor);
		SessionMasterSystem systemM = GuiceContext.get(SessionMasterSystem.class);
		ISystems<?,?> system = systemM.getSystem(enterprise);

		classificationService.create(SessionLastUpdateTime, system);
		classificationService.create(SessionClassifications.SessionObject, system);
		
		IEventService<?> eventsService = GuiceContext.get(IEventService.class);
		
		eventsService.createEventType(SiteVisit.toString(), system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(UserConfirmedAccount.toString(), system, systemM.getSystemToken(enterprise));
		
		classificationService.create(LogonDetails, system);
		classificationService.create(LastLoginTime, system, LogonDetails);
		classificationService.create(LastVisitTime, system, LogonDetails);
		classificationService.create(ConfirmationKey, system, LogonDetails);
		classificationService.create(UserRoles, system, LogonDetails);
		classificationService.create(RememberMe, system, LogonDetails);
		classificationService.create(LoggedOn, system, LogonDetails);
		
	}
	
}
