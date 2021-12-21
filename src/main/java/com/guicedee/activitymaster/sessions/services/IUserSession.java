package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.guicedinjection.representations.IJsonRepresentation;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;


@SuppressWarnings("UnusedReturnValue")
public interface IUserSession<J extends IUserSession<J>>
		extends Serializable, IJsonRepresentation<J>
{
	void clear();
	
	/**
	 * Adds a given value to the session
	 *
	 * @param key    The key to apply to the session
	 * @param object The object for the session
	 * @return Any values required
	 */
	IUserSession<?> addValue(String key, Object object);
	
	boolean hasValue(String key);
	
	/**
	 * Removes a value from the session properties
	 *
	 * @param key The key to update
	 * @return The session
	 */
	IUserSession<?> removeValue(String key);
	
	<T> T as(String key, Class<T> type);
	
	IUserSession<?> setInvolvedParty(IInvolvedParty<?, ?> involvedParty);
	
	IInvolvedParty<?, ?> getInvolvedParty();
	
	ISystems<?, ?> getSystem();
	
	IUserSession<?> setSystem(ISystems<?, ?> system);
	
	Map<String, String> getValues();
	
	UUID getResourceItemID();
	
	IUserSession<?> setResourceItemID(UUID resourceItemID);
	
	UUID getDataID();
	
	IUserSession<?> setDataID(UUID dataID);
}
