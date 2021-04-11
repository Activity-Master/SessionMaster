package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedservlets.services.IOnCallScopeExit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;

public class CallScopeOnExitSessionPersist implements IOnCallScopeExit<CallScopeOnExitSessionPersist>
{
	@Inject
	private ISession<?> session;
	
	@Inject
	private ISessionMasterService<?> service;
	
	@Inject
	private ProfileServiceDTO<?> profileServiceDTO;
	
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?,?> system;
	
	@Inject
	private UserSecurityDTO userSecurityDTO;
	
	@Override
	public void onScopeExit()
	{
		if (profileServiceDTO.getWebClientUUID() != null && profileServiceDTO.findInvolvedParty() != null)
		{
			userSecurityDTO
			               .setLoginExpiresOn(LocalDateTime.now()
			                                               .plus(20, ChronoUnit.MINUTES));
			service.updateSession(profileServiceDTO.findInvolvedParty(), session, system);
		}
	}
}
