package com.armineasy.activitymaster.sessions.services;

public interface ISession<J extends ISession<J>>
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

	/**
	 * Removes a value from the session properties
	 *
	 * @param key
	 * 		The key to update
	 *
	 * @return The session
	 */
	ISession<?> removeValue(String key);
}
