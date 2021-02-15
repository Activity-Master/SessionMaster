package com.guicedee.activitymaster.sessions.services.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = ANY,getterVisibility = NONE,setterVisibility = NONE)
@EqualsAndHashCode(of = {"userName"},callSuper = false)
@Getter
@Setter
@Accessors(chain = true)
public class UserLoginDTO<J extends UserLoginDTO<J>>
		extends ProfileServiceDTO<J>
		implements Serializable
{
	private String userName;
	private String password;
	private boolean rememberMe;
}
