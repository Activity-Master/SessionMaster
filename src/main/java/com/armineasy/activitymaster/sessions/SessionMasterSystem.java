package com.armineasy.activitymaster.sessions;

import com.armineasy.activitymaster.activitymaster.services.IActivityMasterProgressMonitor;
import com.armineasy.activitymaster.activitymaster.services.IActivityMasterSystem;
import com.armineasy.activitymaster.activitymaster.services.dto.IEnterprise;
import com.armineasy.activitymaster.activitymaster.services.dto.ISystems;
import com.armineasy.activitymaster.activitymaster.services.system.IClassificationService;
import com.armineasy.activitymaster.activitymaster.services.system.ISystemsService;
import com.google.inject.Singleton;
import com.jwebmp.guicedinjection.interfaces.JobService;
import com.jwebmp.logger.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.armineasy.activitymaster.sessions.services.classifications.SessionClassifications.*;
import static com.jwebmp.guicedinjection.GuiceContext.*;

@Singleton
public class SessionMasterSystem
		implements IActivityMasterSystem<SessionMasterSystem>
{
	private static final Map<IEnterprise<?>, UUID> systemTokens = new HashMap<>();
	private static final Map<IEnterprise<?>, ISystems> newSystem = new HashMap<>();

	private UUID uuid;

	public static Map<IEnterprise<?>, UUID> getSystemTokens()
	{
		return SessionMasterSystem.systemTokens;
	}

	public static Map<IEnterprise<?>, ISystems> getNewSystem()
	{
		return SessionMasterSystem.newSystem;
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

	private void done(IEnterprise<?> enterprise)
	{
		IClassificationService<?> classificationService = get(IClassificationService.class);

		classificationService.create(SessionLastUpdateTime, newSystem.get(enterprise));


		classificationService.create(SessionObject, newSystem.get(enterprise));
	}

	@Override
	public void postUpdate(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		newSystem.put(enterprise, get(ISystemsService.class)
				                          .create(enterprise, "Sessions Master",
				                                  "The system for handling distributed sessions", ""));
		uuid = get(ISystemsService.class)
				       .registerNewSystem(enterprise, newSystem.get(enterprise));

		LogFactory.getLog("SessionMaster")
		          .warning("Waiting for all systems to generate their security identities");
		JobService.getInstance()
		          .waitForJob("SecurityTokenStore", 5L, TimeUnit.MINUTES);

		systemTokens.put(enterprise, uuid);

		done(enterprise);
	}
}
