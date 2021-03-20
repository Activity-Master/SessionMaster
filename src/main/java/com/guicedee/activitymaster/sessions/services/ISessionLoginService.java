package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.dto.UserLoginDTO;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.sessions.services.dto.UserConfirmationKeyDTO;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;

import java.util.UUID;

public interface ISessionLoginService<J extends ISessionLoginService<J>>
{
	
	UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?,?> system, UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException;
	
	ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?,?> system, UUID... identityToken) throws ProfileServiceException;
	
	ProfileServiceDTO<?> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?,?> system, UUID... identityToken) throws ProfileServiceException;
	
	ProfileServiceDTO<?> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?,?> system, UUID... identityToken) throws ProfileServiceException;
	
	boolean verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?,?> system, UUID... identityToken);
	
	UserLoginDTO<?> verifyPasswordForUser(UserLoginDTO<?> userLoginDTO, IEnterprise<?,?> enterprise, UUID... identityToken);
}
