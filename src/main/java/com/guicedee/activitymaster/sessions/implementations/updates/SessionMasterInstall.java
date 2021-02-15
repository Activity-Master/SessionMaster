package com.guicedee.activitymaster.sessions.implementations.updates;

import com.guicedee.activitymaster.core.services.IActivityMasterProgressMonitor;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.system.IClassificationService;
import com.guicedee.activitymaster.core.services.system.IEventService;
import com.guicedee.activitymaster.core.updates.DatedUpdate;
import com.guicedee.activitymaster.core.updates.ISystemUpdate;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;

import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileEventTypes.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.guicedinjection.GuiceContext.*;

@DatedUpdate(date = "2020/11/01", taskCount = 1)
public class SessionMasterInstall implements ISystemUpdate
{
	@Override
	public void update(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);
		
		logProgress("Session Master", "Loading Default Session Classifications", progressMonitor);
		SessionMasterSystem systemM = GuiceContext.get(SessionMasterSystem.class);
		ISystems<?> system = systemM.getSystem(enterprise);

		classificationService.create(SessionLastUpdateTime, system);
		classificationService.create(SessionClassifications.SessionObject, system);
		
		IEventService<?> eventsService = GuiceContext.get(IEventService.class);
		
		eventsService.createEventType(SiteVisit, system, systemM.getSystemToken(enterprise));
		eventsService.createEventType(UserConfirmedAccount, system, systemM.getSystemToken(enterprise));
		
		classificationService.create(LogonDetails, system);
		classificationService.create(LastLoginTime, system, LogonDetails);
		classificationService.create(LastVisitTime, system, LogonDetails);
		classificationService.create(ConfirmationKey, system, LogonDetails);
		classificationService.create(UserRoles, system, LogonDetails);
		classificationService.create(RememberMe, system, LogonDetails);
		classificationService.create(LoggedOn, system, LogonDetails);
		
	}
	
}
