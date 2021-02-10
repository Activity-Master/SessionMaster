package com.guicedee.activitymaster.sessions;

import com.google.inject.Provider;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.system.IEnterpriseService;
import com.guicedee.activitymaster.core.services.system.IInvolvedPartyService;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.guicedinjection.GuiceContext;
import com.jwebmp.core.base.ajax.AjaxCall;
import com.jwebmp.core.base.ajax.JsonVariable;
import com.jwebmp.core.utilities.StaticStrings;

import java.util.Map;
import java.util.UUID;

public class SessionProvider
		implements Provider<ISession>
{
	@Override
	public ISession<?> get()
	{
		AjaxCall<?> call = GuiceContext.get(AjaxCall.class);
		if(call.getVariable(StaticStrings.LOCAL_STORAGE_VARIABLE_KEY) != null)
		{
			Map<String, String> stringStringMap = call.getVariable(StaticStrings.LOCAL_STORAGE_VARIABLE_KEY)
			                                              .asMap();
			UUID identityToken = UUID.fromString(stringStringMap.get(StaticStrings.LOCAL_STORAGE_PARAMETER_KEY));
			
			
			IEnterprise<?> enterprise = GuiceContext.get(IEnterprise.class);
			
			IInvolvedPartyService<?> involvedPartyService = GuiceContext.get(IInvolvedPartyService.class);
			SessionMasterSystem sms = GuiceContext.get(SessionMasterSystem.class);
			ISystems<?> system = sms.getSystem(enterprise);
			UUID systemToken = sms.getSystemToken(enterprise);
			
			IInvolvedParty<?> byUUID = involvedPartyService.findByIdentificationType("IdentificationTypeWebClientUUID", identityToken.toString());
			
			ISessionMasterService<?> sessionMasterService = GuiceContext.get(ISessionMasterService.class);
			return sessionMasterService.getSession(byUUID, system, systemToken);
		}
		else
		{
			return new Session();
		}
	}
}
