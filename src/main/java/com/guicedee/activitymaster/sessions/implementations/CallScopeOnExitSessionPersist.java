package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.OutOfScopeException;
import com.google.inject.ProvisionException;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedservlets.CallScopeProperties;
import com.guicedee.guicedservlets.services.IOnCallScopeExit;
import lombok.extern.java.Log;

import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.*;
import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

@Log
public class CallScopeOnExitSessionPersist implements IOnCallScopeExit<CallScopeOnExitSessionPersist>
{
	@Override
	public void onScopeExit()
	{
		try
		{
			CallScopeProperties callScopeProperties = GuiceContext.get(CallScopeProperties.class);
			if (callScopeProperties.isWebCall())
			{
				return;
			}
			IUserSession<?> session = GuiceContext.get(IUserSession.class);
			if (session == null)
			{
				return;
			}
			
			if (session.getInvolvedParty() != null)
			{
				UserSecurityDTO us = GuiceContext.get(UserSecurityDTO.class);
				if (us.isLoggedIn())
				{
					us.setLoginExpiresOn(com.entityassist.RootEntity.getNow()
					                                                .plus(us.getSessionTimeout()));
				}
				try
				{
					IUserSessionService<?> sessionMasterService = GuiceContext.get(IUserSessionService.class);
					sessionMasterService.updateSession(session.getInvolvedParty(), session, getISystem(SessionMasterSystemName), getISystemToken(SessionMasterSystemName));
				}
				catch (Throwable T)
				{
					log.log(Level.FINE, "Couldn't update user session, not called from a session?", T);
				}
			}
		}
		catch (OutOfScopeException | ProvisionException e)
		{
			log.log(Level.FINE, "Scope closing but call scope properties not populated", e);
		}
	}
	
}
