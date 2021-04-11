package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;

import java.io.Serializable;


@SuppressWarnings("UnusedReturnValue")
public interface ISession<J extends ISession<J>>
		extends Serializable
{
	/**
	 * Adds a given value to the session
	 *
	 * @param key    The key to apply to the session
	 * @param object The object for the session
	 *
	 * @return Any values required
	 */
	ISession<?> addValue(String key, Object object);
	
	boolean hasValue(String key);
	
	/**
	 * Removes a value from the session properties
	 *
	 * @param key The key to update
	 *
	 * @return The session
	 */
	ISession<?> removeValue(String key);
	
	<T> T as(String key, Class<T> type);
	
	ISession<?> setInvolvedParty(IInvolvedParty<?,?> involvedParty);
	
	IInvolvedParty<?,?> getInvolvedParty();
	
	ISystems<?,?> getSystem();
	
	ISession<?> setSystem(ISystems<?,?> system);
}
