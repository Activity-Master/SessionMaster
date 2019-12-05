package com.guicedee.activitymaster.sessions.services;


import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.sessions.Session;

import java.io.IOException;
import java.io.Serializable;

public interface ISession<J extends ISession<J>>
	extends Serializable
{

	/**
	 * Adds a given value to the session
	 *
	 * @param key
	 * 		The key to apply to the session
	 * @param object
	 * 		The object for the session
	 *
	 * @return Any values required
	 */
	ISession<?> addValue(String key, Object object);

	boolean hasValue(String key);

	/**
	 * Removes a value from the session properties
	 *
	 * @param key
	 * 		The key to update
	 *
	 * @return The session
	 */
	ISession<?> removeValue(String key);

	@SuppressWarnings("unchecked")
	<T> T as(String key, Class<T> type) throws IOException;

	ISession<?> setInvolvedParty(IInvolvedParty<?> involvedParty);

	IInvolvedParty<?> getInvolvedParty();

	IEnterpriseName<?> getEnterpriseName();

	Session setEnterpriseName(IEnterpriseName<?> enterpriseName);
}
