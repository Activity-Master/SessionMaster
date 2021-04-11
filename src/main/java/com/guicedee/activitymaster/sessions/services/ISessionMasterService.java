package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;

import java.util.UUID;

public interface ISessionMasterService<J extends ISessionMasterService<J>>
{
	String SessionMasterSystemName = "Sessions Master";
	/**
	 * Retrieves a persistant session from the UUID
	 *
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param identityToken The requesting tokens security token identity
	 *
	 * @return An ISession that is scoped
	 */
	ISession<?> getSession(IInvolvedParty<?,?> involvedParty, ISystems<?,?> system, UUID... identityToken);
	
	ISession<?> getSession(IInvolvedParty<?,?> involvedParty, ISession<?> original, ISystems<?,?> system, UUID... identityToken);
	
	ISession<?> updateCache(IInvolvedParty<?, ?> involvedParty, ISession<?> original, ISystems<?, ?> system, UUID... identityToken);
	
	ISession<?> expireSession(IInvolvedParty<?,?> involvedParty, ISession<?> original, ISystems<?,?> system, UUID... identityToken);
	
	//ISession<?> createSession(IInvolvedParty<?,?> involvedParty, ISession<?> session, ISystems<?,?> system, UUID... identityToken);
	
	/**
	 * Adds or Updates the Session to the given item
	 *
	 * @param identityToken The secuirity token doing the requesting
	 * @param session       The session to update with
	 *
	 * @return The ISession
	 */
	ISession<?> updateSession(IInvolvedParty<?,?> involvedParty, ISession<?> session, ISystems<?,?> system, UUID... identityToken);
}
