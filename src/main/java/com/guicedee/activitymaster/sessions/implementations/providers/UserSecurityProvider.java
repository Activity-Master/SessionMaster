package com.guicedee.activitymaster.sessions.implementations.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.guicedee.activitymaster.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.GuiceContext;
import com.jwebmp.core.base.ajax.AjaxCall;
import com.jwebmp.core.utilities.StaticStrings;

public class UserSecurityProvider
		implements Provider<UserSecurityDTO>
{
	@Inject
	private ProfileServiceDTO<?> profileServiceDTO;
	@Inject
	private ISession<?> session;
	
	@Override
	public UserSecurityDTO get()
	{
		AjaxCall<?> call = GuiceContext.get(AjaxCall.class);
		if(call.getVariable(StaticStrings.LOCAL_STORAGE_VARIABLE_KEY) != null)
		{
			IInvolvedParty<?,?> involvedParty = profileServiceDTO.findInvolvedParty();
			if (involvedParty == null)
			{
				return new UserSecurityDTO();
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
		else
		{
			return new UserSecurityDTO();
		}
	}
}
