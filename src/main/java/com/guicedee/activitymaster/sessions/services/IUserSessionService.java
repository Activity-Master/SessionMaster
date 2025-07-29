package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import io.smallrye.mutiny.Uni;



public interface IUserSessionService<J extends IUserSessionService<J>>
{
	String SessionMasterSystemName = "Sessions Master";
	
	/**
	 * Retrieves a persistent session from the UUID
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param system        The system context
	 * @param identityToken The requesting tokens security token identity
	 * @return An ISession that is scoped
	 */
	Uni<IUserSession<?>> getSession(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Retrieves a persistent session from the UUID with an original session
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param original      The original session to update
	 * @param system        The system context
	 * @param identityToken The requesting tokens security token identity
	 * @return An ISession that is scoped
	 */
	Uni<IUserSession<?>> getSession(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Updates the cache for a session
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param original      The original session to update
	 * @param system        The system context
	 * @param identityToken The requesting tokens security token identity
	 * @return The updated session
	 */
	Uni<IUserSession<?>> updateCache(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Removes a session from the cache
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @return Void
	 */
	Uni<Void> removeCache(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty);
	
	/**
	 * Expires a session
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param original      The original session to expire
	 * @param system        The system context
	 * @param identityToken The requesting tokens security token identity
	 * @return The expired session
	 */
	Uni<IUserSession<?>> expireSession(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	//ISession<?> createSession(IInvolvedParty<?,?> involvedParty, ISession<?> session, ISystems<?,?> system, java.util.UUID... identityToken);
	
	/**
	 * Adds or Updates the Session to the given item
	 *
	 * @param session       The Mutiny.Session to use for database operations
	 * @param involvedParty The identity (IdentitifcationTypes.IdentityToken) uuid
	 * @param userSession   The session to update with
	 * @param system        The system context
	 * @param identityToken The security token doing the requesting
	 * @return The ISession
	 */
	Uni<IUserSession<?>> updateSession(org.hibernate.reactive.mutiny.Mutiny.Session session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> userSession, ISystems<?, ?> system, java.util.UUID... identityToken);
}
