package com.armineasy.activitymaster.sessions.implementations;

import com.armineasy.activitymaster.sessions.Session;
import com.armineasy.activitymaster.sessions.SessionMasterService;
import com.armineasy.activitymaster.sessions.services.ISession;
import com.armineasy.activitymaster.sessions.services.ISessionMasterService;
import com.google.inject.PrivateModule;
import com.google.inject.servlet.RequestScoped;
import com.jwebmp.guicedinjection.interfaces.IGuiceModule;

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

		bind(ISession.class).to(Session.class)
		                    .in(RequestScoped.class);
		expose(ISession.class);
	}

}
