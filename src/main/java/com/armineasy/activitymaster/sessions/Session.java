package com.armineasy.activitymaster.sessions;

import com.armineasy.activitymaster.sessions.services.ISession;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.servlet.SessionScoped;
import com.jwebmp.logger.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import static com.jwebmp.guicedinjection.GuiceContext.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@SessionScoped
public class Session
		implements ISession<Session>
{
	@JsonValue
	private final Map<String, Object> values = new LinkedHashMap<>();

	private UUID uuid;

	@Override
	public String toString()
	{
		try
		{
			return get(ObjectMapper.class)
					       .writeValueAsString(this);
		}
		catch (JsonProcessingException e)
		{
			LogFactory.getLog("Session")
			          .log(Level.SEVERE, "Couldn't make Session Output", e);
			return "Something went very wrong!" + e.getMessage();
		}
	}

	@Override
	public Session addValue(String key, Object object)
	{
		values.put(key, object);
		return this;
	}

	@Override
	public Session removeValue(String key)
	{
		values.remove(key);
		return this;
	}

	public <T> T as(String key, Class<T> type)
	{
		return (T) values.get(key);
	}

	public UUID getUuid()
	{
		return uuid;
	}

	public Session setUuid(UUID uuid)
	{
		this.uuid = uuid;
		return this;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		Session session = (Session) o;
		return Objects.equals(uuid, session.uuid);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(uuid);
	}
}
