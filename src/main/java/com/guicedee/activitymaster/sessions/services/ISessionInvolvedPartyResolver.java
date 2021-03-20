package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.guicedinjection.interfaces.IDefaultService;

public interface ISessionInvolvedPartyResolver<J extends ISessionInvolvedPartyResolver<J>>
		extends IDefaultService<J>
{
	IInvolvedParty<?,?> resolveInvolvedParty();
}
