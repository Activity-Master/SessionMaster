package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;



public interface IUserSessionService<J extends IUserSessionService<J>>
{
	String SessionMasterSystemName = "Sessions Master";
	
	/**
	 * Retrieves a persistant session from the UUID
	 *
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param identityToken The requesting tokens security token identity
	 * @return An ISession that is scoped
	 */
	IUserSession<?> getSession(IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	IUserSession<?> getSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	IUserSession<?> updateCache(IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	void removeCache(IInvolvedParty<?, ?> involvedParty);
	
	IUserSession<?> expireSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	//ISession<?> createSession(IInvolvedParty<?,?> involvedParty, ISession<?> session, ISystems<?,?> system, java.util.UUID... identityToken);
	
	/**
	 * Adds or Updates the Session to the given item
	 *
	 * @param identityToken The secuirity token doing the requesting
	 * @param session       The session to update with
	 * @return The ISession
	 */
	IUserSession<?> updateSession(IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken);
}
