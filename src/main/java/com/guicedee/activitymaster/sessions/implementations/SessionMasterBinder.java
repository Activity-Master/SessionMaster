package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.*;
import com.guicedee.activitymaster.sessions.*;
import com.guicedee.activitymaster.sessions.implementations.providers.UserSecurityProvider;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.guicedservlets.services.scopes.CallScope;
import com.guicedee.activitymaster.sessions.*;

public class SessionMasterBinder
		extends PrivateModule
		implements IGuiceModule<SessionMasterBinder>
{

	@Override
	protected void configure()
	{
		@SuppressWarnings("Convert2Diamond")
		Key<ISessionMasterService<?>> genericKey = Key.get(new TypeLiteral<ISessionMasterService<?>>() {});
		@SuppressWarnings("Convert2Diamond")
		Key<ISessionMasterService<SessionMasterService>> realKey
				= Key.get(new TypeLiteral<ISessionMasterService<SessionMasterService>>() {});
		
		bind(genericKey).to(realKey);
		bind(realKey).to(SessionMasterService.class);
		bind(ISessionMasterService.class).to(genericKey);
		
		expose(genericKey);
		expose(ISessionMasterService.class);
		
		
		@SuppressWarnings("Convert2Diamond")
		Key<ISession<?>> genericKeySession = Key.get(new TypeLiteral<ISession<?>>() {});
		@SuppressWarnings("Convert2Diamond")
		Key<ISession<Session>> realKeySession
				= Key.get(new TypeLiteral<ISession<Session>>() {});
		
		bind(genericKeySession).to(realKeySession);
		bind(realKeySession).toProvider(SessionProvider.class);
		bind(ISession.class).to(genericKeySession);
		
		expose(genericKeySession);
		expose(ISession.class);
		
		@SuppressWarnings("Convert2Diamond")
		Key<ISessionLoginService<?>> genericKeySessionLoginService = Key.get(new TypeLiteral<ISessionLoginService<?>>() {});
		@SuppressWarnings("Convert2Diamond")
		Key<ISessionLoginService<SessionLoginService>> realKeySessionLoginService
				= Key.get(new TypeLiteral<ISessionLoginService<SessionLoginService>>() {});
		
		bind(genericKeySessionLoginService).to(realKeySessionLoginService);
		bind(realKeySessionLoginService).to(SessionLoginService.class);
		bind(ISessionLoginService.class).to(genericKeySessionLoginService);
		
		expose(genericKeySessionLoginService);
		expose(ISessionLoginService.class);
		
		bind(UserSecurityDTO.class).toProvider(UserSecurityProvider.class);
		expose(UserSecurityDTO.class);
	}

}
