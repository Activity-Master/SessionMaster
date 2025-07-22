package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.guicedee.activitymaster.fsdm.client.services.IInvolvedPartyService;
import com.guicedee.activitymaster.fsdm.client.services.ISystemsService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import io.smallrye.mutiny.Uni;

import com.guicedee.client.IGuiceContext;
import lombok.extern.java.Log;

import java.io.Serial;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;

import static com.guicedee.client.IGuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Log
public class UserSession
		implements IUserSession<UserSession>
{
	@Serial
	private static final long serialVersionUID = 1L;
	@JsonValue
	private final Map<String, String> values = new LinkedHashMap<>();
	
	private IInvolvedParty<?,?> involvedParty;
	//private ISystems<?,?> system;
	
	private UUID resourceItemID;
	private UUID dataID;
	
	@Override
	public String toString()
	{
		try
		{
			return get(DefaultObjectMapper)
					.writerWithDefaultPrettyPrinter()
					.withoutFeatures(SerializationFeature.FAIL_ON_EMPTY_BEANS)
					.writeValueAsString(this);
		}
		catch (JsonProcessingException e)
		{
			log.log(Level.SEVERE, "Couldn't make Session Output", e);
			return "Something went very wrong!" + e.getMessage();
		}
	}
	
	@Override
	public void clear()
	{
		values.clear();
	}
	
	@Override
	public IUserSession<?> addValue(String key, Object object)
	{
		String result;
		if (!(object instanceof String))
		{
			try
			{
				result = get(DefaultObjectMapper).writeValueAsString(object);
			}
			catch (JsonProcessingException e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			result = object.toString();
		}
		if (values.containsKey(key) && values.get(key)
		                                     .equals(result))
		{
			log.log(Level.FINER, "No session update required, value is the same");
		}
		else
		{
			values.put(key, result);
		}
		return this;
	}
	
	@Override
	public boolean hasValue(String key)
	{
		return values.containsKey(key);
	}
	
	@Override
	public IUserSession<?> removeValue(String key)
	{
		values.remove(key);
		return this;
	}
	
	@Override
	public <T> T as(String key, Class<T> type)
	{
		String value = values.get(key);
		try
		{
			if (value == null)
			{
				return null;
			}
			if (type.equals(String.class))
			{
				return (T) value;
			}
			return get(DefaultObjectMapper).readValue(value, type);
		}
		catch (InvalidDefinitionException ide)
		{
			log.log(Level.FINE, MessageFormat.format("Invalid Session Object Deserialization - {0} / {1}", key, type.getSimpleName()));
			return null;
		}
		catch (Throwable e)
		{
			log.log(Level.WARNING, "Unable to deserialize session object - ", e);
			return null;
		}
	}
	
	@Override
	public Uni<IUserSession<?>> setInvolvedParty(IInvolvedParty<?,?> involvedParty)
	{
		this.involvedParty = involvedParty;
		addValue("involved-party", involvedParty.getId().toString());
		
		// Create a new Uni with explicit type
		IUserSession<?> session = this;
		return Uni.createFrom().item(session)
		         .onFailure().invoke(error -> log.log(Level.SEVERE, "Error setting involved party: " + error.getMessage(), error))
		         .map(result -> (IUserSession<?>) result);
	}
	
	@Override
	public Uni<IInvolvedParty<?,?>> getInvolvedParty()
	{
		if (involvedParty != null) {
			// Create a new Uni with explicit type
			IInvolvedParty<?,?> party = this.involvedParty;
			return Uni.createFrom().item(party);
		}
		
		if (hasValue("involved-party")) {
			try {
				IInvolvedPartyService<?> service = get(IInvolvedPartyService.class);
				UUID id = UUID.fromString(as("involved-party", String.class));
				
				// Get the involved party reactively
				return service.findByID(id)
					.onItem().invoke(party -> this.involvedParty = party)
					.onFailure().invoke(error -> log.log(Level.SEVERE, "Error getting involved party: " + error.getMessage(), error));
			} catch (Exception e) {
				log.log(Level.SEVERE, "Error getting involved party: " + e.getMessage(), e);
				return Uni.createFrom().failure(e);
			}
		}
		
		return Uni.createFrom().nullItem();
	}
	
/*
	@Override
	public ISystems<?,?> getSystem()
	{
		if (system == null && involvedParty != null)
		{
			system = IGuiceContext.get(ISystemsService.class).getActivityMaster(null,null,null);
		}
		return system;
	}
	
	@Override
	public UserSession setSystem(ISystems<?,?> system)
	{
		this.system = system;
		return this;
	}*/
	
	@Override
	public Map<String, String> getValues()
	{
		return values;
	}
	
	@Override
	public UUID getResourceItemID()
	{
		return resourceItemID;
	}
	
	@Override
	public UserSession setResourceItemID(UUID resourceItemID)
	{
		this.resourceItemID = resourceItemID;
		return this;
	}
	@Override
	public UUID getDataID()
	{
		return dataID;
	}
	@Override
	public UserSession setDataID(UUID dataID)
	{
		this.dataID = dataID;
		return this;
	}
}