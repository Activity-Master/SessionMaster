package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.fsdm.client.services.annotations.ActivityMasterDB;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.dto.UserSecurityDTO;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedpersistence.db.annotations.Transactional;
import com.guicedee.guicedservlets.services.IOnCallScopeExit;

import java.time.temporal.ChronoUnit;

import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;

public class CallScopeOnExitSessionPersist implements IOnCallScopeExit<CallScopeOnExitSessionPersist>
{
	@Inject
	private ISession<?> session;
	
	@Inject
	private ISessionMasterService<?> service;
	
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?, ?> system;
	
	@Override
	@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	public void onScopeExit()
	{
		if (session.getInvolvedParty() != null)
		{
			UserSecurityDTO us = GuiceContext.get(UserSecurityDTO.class);
			if (us.isLoggedIn())
			{
				us.setLoginExpiresOn(com.entityassist.RootEntity.getNow()
				                                  .plus(20, ChronoUnit.MINUTES));
			}
			service.updateSession(session.getInvolvedParty(), session, system);
		}
	}
}
