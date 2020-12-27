package com.guicedee.activitymaster.sessions.implementations.updates;

import com.guicedee.activitymaster.core.services.IActivityMasterProgressMonitor;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.system.IClassificationService;
import com.guicedee.activitymaster.core.updates.DatedUpdate;
import com.guicedee.activitymaster.core.updates.ISystemUpdate;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;

import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.SessionLastUpdateTime;
import static com.guicedee.guicedinjection.GuiceContext.get;

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
	}
}
