package com.guicedee.activitymaster.sessions;

import com.guicedee.activitymaster.core.services.IActivityMasterProgressMonitor;
import com.guicedee.activitymaster.core.services.IActivityMasterSystem;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.system.IClassificationService;
import com.guicedee.activitymaster.core.services.system.ISystemsService;
import com.google.inject.Singleton;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedinjection.interfaces.JobService;
import com.guicedee.logger.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.guicedee.guicedinjection.GuiceContext.*;

@Singleton
public class SessionMasterSystem
		implements IActivityMasterSystem<SessionMasterSystem>
{
	private static final Map<IEnterprise<?>, UUID> systemTokens = new HashMap<>();
	private static final Map<IEnterprise<?>, ISystems<?>> systemsMap = new HashMap<>();

	public static Map<IEnterprise<?>, UUID> getSystemTokens()
	{
		return SessionMasterSystem.systemTokens;
	}

	public static Map<IEnterprise<?>, ISystems<?>> getSystemsMap()
	{
		return SessionMasterSystem.systemsMap;
	}

	@Override
	public void createDefaults(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{

	}

	@Override
	public int totalTasks()
	{
		return 0;
	}

	private void done(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);

		try
		{
			classificationService.find(SessionLastUpdateTime, enterprise, systemTokens.get(enterprise));
		}
		catch (Exception e)
		{
			logProgress("Mail Master", "Loading Default Mail Session Classifications", progressMonitor);
			classificationService.create(SessionLastUpdateTime, systemsMap.get(enterprise));
			classificationService.create(SessionClassifications.SessionObject, systemsMap.get(enterprise));
		}
	}

	@Override
	public void postStartup(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		final String systemName = "Sessions Master";
		final String systemDesc = "The system for handling distributed sessions";
		ISystems<?> sys = GuiceContext.get(ISystemsService.class)
		                              .findSystem(enterprise, systemName);
		UUID securityToken = null;
		if (sys == null)
		{
			sys = GuiceContext.get(ISystemsService.class)
			                  .create(enterprise, systemName, systemDesc, systemName);
			securityToken = GuiceContext.get(ISystemsService.class)
			                            .registerNewSystem(enterprise, sys);
		}
		else
		{
			securityToken = GuiceContext.get(ISystemsService.class)
			                            .getSecurityIdentityToken(sys);
		}
		systemTokens.put(enterprise, securityToken);
		systemsMap.put(enterprise, sys);
	}

	@Override
	public void loadUpdates(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		done(enterprise, progressMonitor);
	}
}
