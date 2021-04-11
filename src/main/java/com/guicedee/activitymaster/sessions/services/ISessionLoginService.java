package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.dto.UserConfirmationKeyDTO;
import com.guicedee.activitymaster.sessions.services.dto.UserLoginDTO;

import java.util.UUID;

public interface ISessionLoginService<J extends ISessionLoginService<J>>
{
	/**
	 * Logs/Returns a device Non-Organic Involved Party, or a logged in organic involved party
	 *
	 * @param profileServiceDTO
	 * @param system
	 * @param identityToken
	 * @return
	 * @throws ProfileServiceException
	 */
	ProfileServiceDTO<?> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException;
	
	/**
	 * Creates a confirmation key that expires after two hours to a page link that will authenticate the request
	 *
	 * @param userRegistrationDTO
	 * @param system
	 * @param identityToken
	 * @return
	 * @throws UserExistsException
	 * @throws WaitingForConfirmationKeyException
	 */
	UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException;
	
	/**
	 * Marks a user as logged in, with already verified as false
	 *
	 * @param profileServiceDTO
	 * @param system
	 * @param identityToken
	 * @return
	 * @throws ProfileServiceException
	 */
	ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException;
	
	/**
	 * Logs a user out, and moves the current IP to the device IP
	 *
	 * @param profileServiceDTO
	 * @param system
	 * @param identityToken
	 * @return
	 * @throws ProfileServiceException
	 */
	ProfileServiceDTO<?> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException;
	
	/**
	 * Logs in a user with already verified flag, if false the system will attempt to authenticate with the details in the dto
	 *
	 * @param profileServiceDTO
	 * @param alreadyVerified
	 * @param system
	 * @param identityToken
	 * @return
	 * @throws ProfileServiceException
	 */
	ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException;
	
	boolean verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, UUID... identityToken);
	
	UserLoginDTO<?> verifyPasswordForUser(UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, UUID... identityToken);
}
