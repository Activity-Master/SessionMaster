package com.guicedee.activitymaster.sessions.services.dto;

import com.fasterxml.jackson.annotation.*;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;

import java.io.Serializable;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, setterVisibility = NONE)
public class UserLoginDTO<J extends UserLoginDTO<J>>
		extends ProfileServiceDTO<J>
		implements Serializable
{
	private String userName;
	private String password;
	private boolean rememberMe;
	
	public String getUserName()
	{
		return userName;
	}
	
	public UserLoginDTO<J> setUserName(String userName)
	{
		this.userName = userName;
		return this;
	}
	
	public String getPassword()
	{
		return password;
	}
	
	public UserLoginDTO<J> setPassword(String password)
	{
		this.password = password;
		return this;
	}
	
	public boolean isRememberMe()
	{
		return rememberMe;
	}
	
	public UserLoginDTO<J> setRememberMe(boolean rememberMe)
	{
		this.rememberMe = rememberMe;
		return this;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof UserLoginDTO))
		{
			return false;
		}
		UserLoginDTO<?> that = (UserLoginDTO<?>) o;
		return Objects.equals(getUserName(), that.getUserName());
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(getUserName());
	}
}
