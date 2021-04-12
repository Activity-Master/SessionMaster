package com.guicedee.activitymaster.sessions.implementations.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.GuiceContext;

public class UserSecurityProvider
		implements Provider<UserSecurityDTO>
{
	@Inject
	private ISession<?> session;
	
	@Override
	public UserSecurityDTO get()
	{
		if (session == null)
		{
			GuiceContext.inject()
			            .injectMembers(this);
		}
		UserSecurityDTO us;
		if (session.hasValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME))
		{
			us = session.as(UserSecurityDTO.USER_SECURITY_SESSION_NAME, UserSecurityDTO.class);
		}
		else
		{
			us = new UserSecurityDTO();
			session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		}
		return us;
	}
}
