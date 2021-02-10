package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.PrivateModule;
import com.google.inject.servlet.RequestScoped;
import com.guicedee.activitymaster.sessions.SessionMasterService;
import com.guicedee.activitymaster.sessions.SessionProvider;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;

public class SessionMasterBinder
		extends PrivateModule
		implements IGuiceModule<SessionMasterBinder>
{

	@Override
	protected void configure()
	{
		bind(ISessionMasterService.class)
				.to(SessionMasterService.class);
		expose(ISessionMasterService.class);
		
		bind(ISession.class).toProvider(SessionProvider.class);
		expose(ISession.class);
	}

}
