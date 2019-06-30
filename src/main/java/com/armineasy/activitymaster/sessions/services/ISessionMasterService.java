package com.armineasy.activitymaster.sessions.services;

import com.armineasy.activitymaster.activitymaster.services.classifications.enterprise.IEnterpriseName;

import java.util.UUID;

public interface ISessionMasterService<J extends ISessionMasterService<J>>
{
	/**
	 * Retrieves a persistant session from the UUID
	 *
	 * @param identityUUID
	 * 		The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param identityToken
	 * 		The requesting tokens security token identity
	 *
	 * @return An ISession that is scoped
	 */
	public ISession<?> getSession(UUID identityUUID, IEnterpriseName<?> enterpriseName, UUID... identityToken);

	/**
	 * Adds or Updates the Session to the given item
	 *
	 * @param identityUUID
	 * 		The identity UUID of the session to update
	 * @param identityToken
	 * 		The secuirity token doing the requesting
	 * @param session
	 * 		The session to update with
	 *
	 * @return The ISession
	 */
	public ISession<?> updateSession(ISession<?> session, UUID identityUUID, UUID... identityToken);
}
