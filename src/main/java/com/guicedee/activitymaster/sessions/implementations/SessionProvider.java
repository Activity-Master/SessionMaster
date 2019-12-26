package com.guicedee.activitymaster.sessions.implementations;

import com.google.inject.Provider;
import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.core.services.system.IInvolvedPartyService;
import com.guicedee.activitymaster.sessions.Session;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.logger.LogFactory;
import com.jwebmp.core.base.servlets.SessionStorageProperties;
import com.jwebmp.core.utilities.StaticStrings;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SessionProvider
		implements Provider<ISession>
{
	@Override
	public ISession<?> get()
	{
		try
		{
			SessionStorageProperties<?> storageProperties = GuiceContext.get(SessionStorageProperties.class);
			Map<String, String> localStorage = storageProperties.getLocalStorage();
			if (localStorage.containsKey(StaticStrings.LOCAL_STORAGE_PARAMETER_KEY))
			{
				String localKey = localStorage.get(StaticStrings.LOCAL_STORAGE_PARAMETER_KEY);
				IInvolvedPartyService<?> ipService = GuiceContext.get(IInvolvedPartyService.class);
				IInvolvedParty<?> ip = ipService.findByIdentificationType("IdentificationTypeWebClientUUID", localKey);
				IEnterprise<?> ent = ip.getEnterpriseID();
				if (ent == null)
				{
					System.out.println("Here");
				}
				IEnterpriseName<?> eName = ent.getIEnterprise();
				if (eName == null)
				{
					System.out.println("Here");
				}
				ISessionMasterService<?> sessionMasterService = GuiceContext.get(ISessionMasterService.class);
				UUID systemToken = SessionMasterSystem.getSystemTokens()
				                                      .get(ent);
				if (systemToken == null)
				{
					System.out.println("Here");
				}
				return sessionMasterService.getSession(ip, eName, systemToken);
			}
		}
		catch (Exception e)
		{
			LogFactory.getLog(getClass())
			          .log(Level.WARNING, "Unable to retrieve session", e);
		}
		return new Session();
	}
}
