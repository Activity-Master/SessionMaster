package com.guicedee.activitymaster.sessions.services.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.deserializers.LocalDateTimeDeserializer;
import com.guicedee.activitymaster.profiles.deserializers.LocalDateTimeSerializer;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;
import static com.guicedee.client.IGuiceContext.*;
import static java.time.temporal.ChronoUnit.*;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = ANY,
                getterVisibility = NONE,
                setterVisibility = NONE)
public class UserSecurityDTO
		implements Serializable, IJsonRepresentation<UserSecurityDTO>
{
	@Serial
	private static final long serialVersionUID = 1L;
	
	public static final String USER_SECURITY_SESSION_NAME = "user-security";
	
	private boolean loggedIn;
	private boolean rememberMe;
	private String lastIpAddress;
	private String lastHeader;
	
	private Duration sessionTimeout;
	
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)
	@JsonSerialize(using = LocalDateTimeSerializer.class)
	private LocalDateTime loginExpiresOn = LocalDateTime.now()
	                                                    .plusMinutes(20);
	
	public UserSecurityDTO()
	{
		//No config required
	}
	
	public Duration getSessionTimeout()
	{
		if (sessionTimeout == null)
		{
			sessionTimeout = Duration.of(20, MINUTES);
		}
		return sessionTimeout;
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
		IUserSession<?> session = get(IUserSession.class);
		
		//ISystems<?, ?> system = session.getSystem();
		if (!session.hasValue(USER_SECURITY_SESSION_NAME))
		{
			return false;
		}
		
		UserSecurityDTO us = session.as(USER_SECURITY_SESSION_NAME, UserSecurityDTO.class);
		if (us == null ||
		    us.getLoginExpiresOn() == null ||
		    us.getLoginExpiresOn()
		      .isBefore(com.entityassist.RootEntity.getNow()))
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
	
	public String getLastHeader()
	{
		return lastHeader;
	}
	
	public UserSecurityDTO setLastHeader(String lastHeader)
	{
		this.lastHeader = lastHeader;
		return this;
	}
}
