package com.guicedee.activitymaster.sessions.implementations.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.GuiceContext;
import com.jwebmp.core.base.ajax.AjaxCall;
import com.jwebmp.core.utilities.StaticStrings;

import java.util.UUID;

public class UserSecurityProvider
		implements Provider<UserSecurityDTO>
{
	@Inject
	private ProfileServiceDTO profileServiceDTO;
	
	@Override
	public UserSecurityDTO get()
	{
		AjaxCall<?> call = GuiceContext.get(AjaxCall.class);
		if(call.getVariable(StaticStrings.LOCAL_STORAGE_VARIABLE_KEY) != null)
		{
			IInvolvedParty<?> involvedParty = profileServiceDTO.findInvolvedParty();
			if (involvedParty == null)
			{
				return new UserSecurityDTO();
			}
			IEnterprise<?> enterprise = involvedParty.getEnterprise();
			
			ISystems<?> system = GuiceContext.get(SessionMasterSystem.class)
			                                 .getSystem(enterprise);
			UUID systemToken = GuiceContext.get(SessionMasterSystem.class)
			                               .getSystemToken(enterprise);
			
			ISessionMasterService<?> sessionMasterService = GuiceContext.get(ISessionMasterService.class);
			ISession<?> session = sessionMasterService.getSession(involvedParty, system, systemToken);
			UserSecurityDTO us;
			if (session.hasValue("user-security"))
			{
				us = session.as("user-security", UserSecurityDTO.class);
			}
			else
			{
				us = new UserSecurityDTO();
				session.addValue("user-security", us);
			}
			return us;
		}
		else
		{
			return new UserSecurityDTO();
		}
	}
}
