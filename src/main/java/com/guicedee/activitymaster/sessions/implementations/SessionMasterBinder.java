package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.*;
import com.guicedee.activitymaster.sessions.*;
import com.guicedee.activitymaster.sessions.implementations.providers.UserSecurityProvider;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.guicedservlets.services.scopes.CallScope;

public class SessionMasterBinder
		extends PrivateModule
		implements IGuiceModule<SessionMasterBinder>
{

	@Override
	protected void configure()
	{
		@SuppressWarnings("Convert2Diamond")
		Key<IUserSessionService<?>> genericKey = Key.get(new TypeLiteral<IUserSessionService<?>>() {});
		@SuppressWarnings("Convert2Diamond")
		Key<IUserSessionService<UserSessionService>> realKey
				= Key.get(new TypeLiteral<IUserSessionService<UserSessionService>>() {});
		
		bind(genericKey).to(realKey);
		bind(realKey).to(UserSessionService.class);
		bind(IUserSessionService.class).to(genericKey);
		
		expose(genericKey);
		expose(IUserSessionService.class);
		
		
		@SuppressWarnings("Convert2Diamond")
		Key<IUserSession<?>> genericKeySession = Key.get(new TypeLiteral<IUserSession<?>>() {});
		@SuppressWarnings("Convert2Diamond")
		Key<IUserSession<UserSession>> realKeySession
				= Key.get(new TypeLiteral<IUserSession<UserSession>>() {});
		
		bind(genericKeySession).to(realKeySession).in(CallScope.class);
		bind(realKeySession).toProvider(UserSessionProvider.class).in(CallScope.class);
		bind(IUserSession.class).to(genericKeySession).in(CallScope.class);
		
		expose(genericKeySession);
		expose(IUserSession.class);
		
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
