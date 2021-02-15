package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.PrivateModule;
import com.google.inject.servlet.RequestScoped;
import com.guicedee.activitymaster.sessions.*;
import com.guicedee.activitymaster.sessions.implementations.providers.UserSecurityProvider;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
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
		
		bind(ISessionLoginService.class).to(SessionLoginService.class);
		expose(ISessionLoginService.class);
		
		bind(UserSecurityDTO.class).toProvider(UserSecurityProvider.class);
		expose(UserSecurityDTO.class);
	}

}
