package com.guicedee.activitymaster.sessions.services.dto;

import com.fasterxml.jackson.annotation.*;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = ANY,
                getterVisibility = NONE,
                setterVisibility = NONE)
@EqualsAndHashCode(callSuper = true, of = {})
public class UserConfirmationKeyDTO<J extends UserConfirmationKeyDTO<J>>
		extends ProfileServiceDTO<J>
{
	private UUID confirmationKey;
	
	public UserConfirmationKeyDTO()
	{
	}
	
	public UUID getConfirmationKey()
	{
		return this.confirmationKey;
	}
	
	public UserConfirmationKeyDTO<J> setConfirmationKey(UUID confirmationKey)
	{
		this.confirmationKey = confirmationKey;
		return this;
	}
	
}
