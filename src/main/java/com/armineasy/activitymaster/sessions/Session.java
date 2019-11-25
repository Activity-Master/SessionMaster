package com.armineasy.activitymaster.sessions;

import com.armineasy.activitymaster.activitymaster.services.classifications.enterprise.IEnterpriseName;
import com.armineasy.activitymaster.activitymaster.services.dto.IInvolvedParty;
import com.armineasy.activitymaster.activitymaster.services.system.IEnterpriseService;
import com.armineasy.activitymaster.sessions.services.ISession;
import com.armineasy.activitymaster.sessions.services.ISessionMasterService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.servlet.RequestScoped;
import com.guicedee.logger.LogFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@RequestScoped
public class Session
		implements ISession<Session>
{
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
	public ISession<?> addValue(String key, Object object)
	{
		try
		{
			values.put(key, get(DefaultObjectMapper).writeValueAsString(object));
			ISessionMasterService<?> sessionMasterService = get(ISessionMasterService.class);
			sessionMasterService.updateSession(this, SessionMasterSystem.getSystemTokens()
			                                                            .get(get(IEnterpriseService.class).getEnterprise(getEnterpriseName())));
		}
		catch (JsonProcessingException e)
		{
			LogFactory.getLog("SessionSaved")
			          .log(Level.SEVERE, "Unable to serialize the given object for persistence", e);
		}
		return this;
	}

	@Override
	public boolean hasValue(String key)
	{
		return values.containsKey(key);
	}

	@Override
	public ISession<?> removeValue(String key)
	{
		values.remove(key);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T as(String key, Class<T> type) throws IOException
	{
		String value = values.get(key);
		return get(DefaultObjectMapper).readValue(value, type);
	}

	@Override
	public ISession<?> setInvolvedParty(IInvolvedParty<?> involvedParty)
	{
		this.involvedParty = involvedParty;
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
		return this;
	}

	public Map<String, String> getValues()
	{
		return values;
	}
}
