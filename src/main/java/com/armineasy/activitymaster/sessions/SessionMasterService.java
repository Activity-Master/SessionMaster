package com.armineasy.activitymaster.sessions;

import com.armineasy.activitymaster.activitymaster.services.classifications.enterprise.IEnterpriseName;
import com.armineasy.activitymaster.activitymaster.services.dto.IEnterprise;
import com.armineasy.activitymaster.activitymaster.services.dto.IInvolvedParty;
import com.armineasy.activitymaster.activitymaster.services.dto.ISystems;
import com.armineasy.activitymaster.activitymaster.services.system.IEnterpriseService;
import com.armineasy.activitymaster.activitymaster.services.system.IInvolvedPartyService;
import com.armineasy.activitymaster.activitymaster.services.types.IdentificationTypes;
import com.armineasy.activitymaster.sessions.services.ISession;
import com.armineasy.activitymaster.sessions.services.ISessionMasterService;
import com.google.inject.Singleton;

import java.util.UUID;

import static com.jwebmp.guicedinjection.GuiceContext.*;

@Singleton
public class SessionMasterService
		implements ISessionMasterService<SessionMasterService>
{

	@Override
	public ISession<?> getSession(UUID identityUUID, IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(enterpriseName);
		ISystems<?> sessionSystem = SessionMasterSystem.getNewSystem()
		                                               .get(enterprise);
		IInvolvedPartyService involvedPartyService = get(IInvolvedPartyService.class);
		IInvolvedParty<?> party = involvedPartyService.findByIdentificationType(IdentificationTypes.IdentificationTypeUUID, identityToken.toString(),
		                                                                        sessionSystem, identityToken);
		if (party == null)
		{
			//Involved party does not exist - do nothing?
		}
		else
		{
			//party.hasResourceItem()
		}

		return null;
	}

	@Override
	public ISession<?> updateSession(ISession<?> session, UUID identityUUID, UUID... identityToken)
	{
		return null;
	}
}
