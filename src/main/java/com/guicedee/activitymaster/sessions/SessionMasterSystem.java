package com.guicedee.activitymaster.sessions;

import com.google.inject.*;
import com.guicedee.activitymaster.core.services.IActivityMasterProgressMonitor;
import com.guicedee.activitymaster.core.services.IActivityMasterSystem;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.system.ActivityMasterDefaultSystem;
import com.guicedee.activitymaster.core.services.system.ISystemsService;

import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;

@Singleton
public class SessionMasterSystem
		extends ActivityMasterDefaultSystem<SessionMasterSystem>
		implements IActivityMasterSystem<SessionMasterSystem>
{
	@Inject
	private Provider<ISystemsService<?>> systemsService;
	
	@Override
	public void registerSystem(IEnterprise<?> enterprise, IActivityMasterProgressMonitor progressMonitor)
	{
		systemsService.get()
		              .create(enterprise, getSystemName(), getSystemDescription());
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
