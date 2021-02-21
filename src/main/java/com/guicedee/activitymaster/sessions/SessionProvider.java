package com.guicedee.activitymaster.sessions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;

import java.util.UUID;

import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;

public class SessionProvider
		implements Provider<ISession<Session>>
{
	@Inject
	private IEnterprise<?> enterprise;
	
	@Inject
	private ProfileServiceDTO<?> profileServiceDTO;
	
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?> sessionSystem;
	@Inject
	@Named(SessionMasterSystemName)
	private UUID systemToken;
	
	@Inject
	private ISessionMasterService<?> sessionMasterService;
	
	@Override
	public ISession<Session> get()
	{
		if (enterprise.isFake())
		{
			return new Session();
		}
		try
		{
			IInvolvedParty<?> byUUID = profileServiceDTO.findInvolvedParty();
			//noinspection unchecked
			return (ISession<Session>) sessionMasterService.getSession(byUUID, sessionSystem, systemToken);
		}catch (Throwable T)
		{
			return new Session();
		}
	}
}
