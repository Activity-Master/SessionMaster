package com.guicedee.activitymaster.sessions;

import com.google.inject.*;
import com.google.inject.name.Names;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.*;
import lombok.extern.java.Log;

import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.*;
import static com.guicedee.activitymaster.sessions.services.IUserSessionService.*;

@Log
public class UserSessionProvider
		implements Provider<IUserSession<UserSession>>
{
	@Inject
	private IEnterprise<?, ?> enterprise;
	
	@Inject
	private IUserSessionService<?> sessionMasterService;
	
	@Inject
	private IInvolvedPartyService<?> involvedPartyService;
	
	@Override
	public IUserSession<UserSession> get()
	{
		if (sessionMasterService == null)
		{
			com.guicedee.client.IGuiceContext.instance().inject()
			            .injectMembers(this);
		}
		if (enterprise.isFake())
		{
			return null;
		}
	
		UUID localStorageKey = com.guicedee.client.IGuiceContext.get(Key.get(UUID.class, Names.named("localstorage")));
		if (localStorageKey == null)
		{
			//this call does not have any local storage attached? Perhaps from a web call?
			return null;
		}
		else
		{
			try
			{
				IInvolvedParty<?, ?> byUUID = involvedPartyService.get();
				byUUID = byUUID.builder()
				               .find(localStorageKey.toString())
				               .get()
				               .orElse(null);
				
				if (byUUID == null)
				{
					ISessionLoginService<?> sessionLoginService = com.guicedee.client.IGuiceContext.get(ISessionLoginService.class);
					ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
					ProfileServiceDTO<?> profileServiceDTO = sessionLoginService.loginVisitor(dto, getISystem(SessionMasterSystemName), getISystemToken(SessionMasterSystemName));
					byUUID = profileServiceDTO.findInvolvedParty();
				}
				//noinspection unchecked
				return (IUserSession<UserSession>) sessionMasterService.getSession(byUUID, getISystem(SessionMasterSystemName), getISystemToken(SessionMasterSystemName));
			}
			catch (Throwable T)
			{
				log.log(Level.SEVERE, "Cannot work on session", T);
				return new UserSession();
			}
		}
	}
}
