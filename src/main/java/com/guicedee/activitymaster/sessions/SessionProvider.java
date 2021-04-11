package com.guicedee.activitymaster.sessions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedservlets.services.scopes.CallScope;
import lombok.extern.java.Log;

import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;

@Log
@CallScope
public class SessionProvider
		implements Provider<ISession<Session>>
{
	@Inject
	private IEnterprise<?,?> enterprise;
	
	@Inject
	private ISessionLoginService<?> sessionLoginService;
	
	@Inject
	private ProfileServiceDTO<?> profileServiceDTO;
	
	@Inject
	@Named(SessionMasterSystemName)
	private Provider<ISystems<?,?>> system;
	
	@Inject
	@Named(SessionMasterSystemName)
	private Provider<UUID> identityToken;
	
	@Inject
	private ISessionMasterService<?> sessionMasterService;
	
	@Override
	public ISession<Session> get()
	{
		if (sessionMasterService == null)
		{
			GuiceContext.inject()
			            .injectMembers(this);
		}
		if (enterprise.isFake())
		{
			return new Session();
		}
		try
		{
			if(profileServiceDTO.getWebClientUUID() != null)
			{
				IInvolvedParty<?, ?> byUUID = profileServiceDTO.findInvolvedParty();
				if (byUUID == null)
				{
					//create a device identified involved party, and create the session for him
					ProfileServiceDTO<?> profileServiceDTO = sessionLoginService.loginVisitor(this.profileServiceDTO, system.get(), identityToken.get());
					byUUID = profileServiceDTO.findInvolvedParty();
				}
				//noinspection unchecked
				return (ISession<Session>) sessionMasterService.getSession(byUUID, system.get(), identityToken.get());
			}
			else
			{
				return new Session();
			}
		}catch (Throwable T)
		{
			log.log(Level.SEVERE,"Cannot work on session",T);
			return new Session();
		}
	}
}
