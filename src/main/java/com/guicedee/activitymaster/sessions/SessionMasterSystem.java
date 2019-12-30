package com.guicedee.activitymaster.sessions;

import com.google.inject.Singleton;
import com.guicedee.activitymaster.core.services.IActivityMasterProgressMonitor;
import com.guicedee.activitymaster.core.services.IActivityMasterSystem;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.system.ActivityMasterDefaultSystem;
import com.guicedee.activitymaster.core.services.system.IClassificationService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;

import java.util.UUID;

import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.guicedinjection.GuiceContext.*;

@Singleton
public class SessionMasterSystem
		extends ActivityMasterDefaultSystem<SessionMasterSystem>
		implements IActivityMasterSystem<SessionMasterSystem>
{
	@Override
	public void createDefaults(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{

	}

	@Override
	public int totalTasks()
	{
		return 2;
	}

	@Override
	public void loadUpdates(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		done(enterprise, progressMonitor);
	}

	private void done(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);
		try
		{
			UUID token = getSystemToken(enterprise);
			classificationService.find(SessionLastUpdateTime, enterprise, token);
		}
		catch (Exception e)
		{
			logProgress("Session Master", "Loading Default Session Classifications", progressMonitor);
			ISystems<?> system = getSystem(enterprise);
			classificationService.create(SessionLastUpdateTime, system);
			classificationService.create(SessionClassifications.SessionObject, system);
		}
	}

	@Override
	public String getSystemName()
	{
		return "Sessions Master";
	}

	@Override
	public String getSystemDescription()
	{
		return "The system for handling distributed sessions";
	}
}
