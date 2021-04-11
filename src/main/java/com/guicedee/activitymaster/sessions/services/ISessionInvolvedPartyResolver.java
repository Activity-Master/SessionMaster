package com.guicedee.activitymaster.sessions.services;

import com.guicedee.guicedinjection.interfaces.IDefaultService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;

public interface ISessionInvolvedPartyResolver<J extends ISessionInvolvedPartyResolver<J>>
		extends IDefaultService<J>
{
	IInvolvedParty<?,?> resolveInvolvedParty();
}
