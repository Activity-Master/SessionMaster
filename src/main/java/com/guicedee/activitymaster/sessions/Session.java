package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.servlet.RequestScoped;
import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.core.services.system.IEnterpriseService;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.logger.LogFactory;

import javax.cache.annotation.CacheKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@RequestScoped
public class Session
		implements ISession<Session>
{
	private static final long serialVersionUID = 1L;

	private static final Logger log = LogFactory.getLog("Session");
	@JsonValue
	private final Map<String, String> values = new LinkedHashMap<>();

	private IInvolvedParty<?> involvedParty;
	private IEnterpriseName<?> enterpriseName;

	@Override
	public String toString()
	{
		try
		{
			return get(DefaultObjectMapper)
					       .writerWithDefaultPrettyPrinter()
					       .withoutFeatures(SerializationFeature.FAIL_ON_EMPTY_BEANS)
					       .writeValueAsString(this)
					       .replaceAll("\n", "<br/>")
					       .replaceAll("\t", "&nbsp;")
					       .replace("\\\"", "\"");
		}
		catch (JsonProcessingException e)
		{
			log.log(Level.SEVERE, "Couldn't make Session Output", e);
			return "Something went very wrong!" + e.getMessage();
		}
	}

	@Override
	public ISession<?> addValue(String key, Object object)
	{
		try
		{
			if (!(object instanceof String))
			{
				values.put(key, get(DefaultObjectMapper).writeValueAsString(object));
			}
			else
			{
				values.put(key, object.toString());
			}
			ISessionMasterService<?> sessionMasterService = get(ISessionMasterService.class);
			sessionMasterService.updateSession(involvedParty, this, getEnterpriseName(),
			                                   get(SessionMasterSystem.class).getSystemToken(
					                                   get(IEnterpriseService.class).getEnterprise(getEnterpriseName()))
			                                  );
		}
		catch (JsonProcessingException e)
		{
			log.log(Level.SEVERE, "Unable to serialize the given object for persistence", e);
		}
		return this;
	}

	@Override
	public boolean hasValue(String key)
	{
		return values.containsKey(key);
	}

	@Override
	public ISession<?> removeValue(@CacheKey String key)
	{
		values.remove(key);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T as(@CacheKey String key, Class<T> type)
	{
		String value = values.get(key);
		try
		{
			if (value == null)
			{
				return null;
			}
			return get(DefaultObjectMapper).readValue(value, type);
		}
		catch (JsonProcessingException e)
		{
			log.log(Level.WARNING, "Unable to deserialize session object - ", e);
			return null;
		}
	}

	@Override
	public ISession<?> setInvolvedParty(IInvolvedParty<?> involvedParty)
	{
		this.involvedParty = involvedParty;
		values.put("involved-party", involvedParty.getId()
		                                          .toString());
		return this;
	}

	@Override
	public IInvolvedParty<?> getInvolvedParty()
	{
		return involvedParty;
	}

	@Override
	public IEnterpriseName<?> getEnterpriseName()
	{
		return enterpriseName;
	}

	@Override
	public Session setEnterpriseName(IEnterpriseName<?> enterpriseName)
	{
		this.enterpriseName = enterpriseName;
		values.put("enterprise", enterpriseName.toString());
		return this;
	}

	public Map<String, String> getValues()
	{
		return values;
	}
}
