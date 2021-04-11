package com.guicedee.activitymaster.sessions.services.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.guicedinjection.json.LocalDateTimeDeserializer;
import com.guicedee.guicedinjection.json.LocalDateTimeSerializer;
import com.guicedee.guicedservlets.services.scopes.CallScope;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;
import static com.guicedee.guicedinjection.GuiceContext.*;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = ANY,
                getterVisibility = NONE,
                setterVisibility = NONE)
@CallScope
public class UserSecurityDTO
		implements Serializable
{
	@Serial
	private static final long serialVersionUID = 1L;
	
	public static final String USER_SECURITY_SESSION_NAME = "user-security";
	
	private boolean loggedIn;
	private boolean rememberMe;
	private String lastIpAddress;
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)
	@JsonSerialize(using = LocalDateTimeSerializer.class)
	private LocalDateTime loginExpiresOn = LocalDateTime.now()
	                                                    .plusMinutes(20);
	
	public UserSecurityDTO()
	{
		//No config required
	}
	
	public boolean isLoggedIn()
	{
		return loggedIn;
	}
	
	public UserSecurityDTO setLoggedIn(boolean loggedIn)
	{
		this.loggedIn = loggedIn;
		return this;
	}
	
	public boolean isRememberMe()
	{
		return rememberMe;
	}
	
	public UserSecurityDTO setRememberMe(boolean rememberMe)
	{
		this.rememberMe = rememberMe;
		return this;
	}
	
	public String getLastIpAddress()
	{
		return lastIpAddress;
	}
	
	public UserSecurityDTO setLastIpAddress(String lastIpAddress)
	{
		this.lastIpAddress = lastIpAddress;
		return this;
	}
	
	public LocalDateTime getLoginExpiresOn()
	{
		return loginExpiresOn;
	}
	
	public UserSecurityDTO setLoginExpiresOn(LocalDateTime loginExpiresOn)
	{
		this.loginExpiresOn = loginExpiresOn;
		return this;
	}
	
	@JsonIgnore
	public boolean determineIsLoggedIn(boolean asVisitor)
	{
		ISession<?> session = get(ISession.class);
		
		ISystems<?, ?> system = session.getSystem();
		if (!session.hasValue(USER_SECURITY_SESSION_NAME))
		{
			return false;
		}
		
		UserSecurityDTO us = session.as(USER_SECURITY_SESSION_NAME, UserSecurityDTO.class);
		if (us == null ||
		    us.getLoginExpiresOn() == null ||
		    us.getLoginExpiresOn()
		      .isBefore(LocalDateTime.now()))
		{
			return false;
		}
		
		boolean loggedOn = false;
		try
		{
			loggedOn = us.isLoggedIn();
		}
		catch (Exception nsfe)
		{
			loggedOn = false;
		}
		
		if (loggedOn && !asVisitor)
		{
			return true;
		}
		
		if (us.isRememberMe())
		{
			return loggedOn;
		}
		return false;
	}
}
