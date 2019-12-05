package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;

import java.util.UUID;

public interface ISessionMasterService<J extends ISessionMasterService<J>>
{
	/**
	 * Retrieves a persistant session from the UUID
	 *
	 * @param involvedParty
	 * 		The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param identityToken
	 * 		The requesting tokens security token identity
	 *
	 * @return An ISession that is scoped
	 */
	public ISession<?> getSession(IInvolvedParty<?> involvedParty, IEnterpriseName<?> enterpriseName, UUID... identityToken);

	/**
	 * Adds or Updates the Session to the given item
	 *
	 * @param identityToken
	 * 		The secuirity token doing the requesting
	 * @param session
	 * 		The session to update with
	 *
	 * @return The ISession
	 */
	public ISession<?> updateSession(ISession<?> session, UUID... identityToken);
}
