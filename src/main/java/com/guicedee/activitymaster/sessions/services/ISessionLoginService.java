package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.dto.UserConfirmationKeyDTO;
import com.guicedee.activitymaster.sessions.services.dto.UserLoginDTO;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;


public interface ISessionLoginService<J extends ISessionLoginService<J>>
{
	/**
	 * Logs/Returns a device Non-Organic Involved Party, or a logged in organic involved party
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The profile service DTO containing client information
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the ProfileServiceDTO with login information
	 */
	Uni<ProfileServiceDTO<?>> loginVisitor(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken);

	/**
	 * Sets a user as logged in
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param newIp The involved party to set as logged in
	 * @param profileServiceDTO The profile service DTO
	 * @param rememberMe Whether to remember the login
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni that completes when the user is set as logged in
	 */
	Uni<Void> setUserLoggedIn(Mutiny.Session session, IInvolvedParty<?, ?> newIp,
	                    ProfileServiceDTO<?> profileServiceDTO,
	                     boolean rememberMe,
	                     ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Sets a user as logged out
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param involvedParty The involved party to set as logged out
	 * @param deviceIP The device IP
	 * @param profileServiceDTO The profile service DTO
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni that completes when the user is set as logged out
	 */
	Uni<Void> setUserLoggedOut(Mutiny.Session session, IInvolvedParty<?, ?> involvedParty,
	                      IInvolvedParty<?, ?> deviceIP,
	                      ProfileServiceDTO<?> profileServiceDTO,
	                      ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Creates a confirmation key that expires after two hours to a page link that will authenticate the request
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param userRegistrationDTO The user registration DTO containing registration information
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the UserConfirmationKeyDTO with confirmation information
	 * 
	 * Note: UserExistsException and WaitingForConfirmationKeyException are handled within the reactive chain
	 */
	Uni<UserConfirmationKeyDTO<?>> registerVisitor(Mutiny.Session session, UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Marks a user as logged in, with already verified as false
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The login DTO containing user credentials
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the ProfileServiceDTO with login information
	 * 
	 * Note: ProfileServiceException is handled within the reactive chain
	 */
	Uni<ProfileServiceDTO<?>> loginUser(Mutiny.Session session, UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Logs a user out, and moves the current IP to the device IP
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The profile service DTO
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the ProfileServiceDTO with logout information
	 * 
	 * Note: ProfileServiceException is handled within the reactive chain
	 */
	Uni<ProfileServiceDTO<?>> logoutUser(Mutiny.Session session, ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Logs in a user with already verified flag, if false the system will attempt to authenticate with the details in the dto
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param profileServiceDTO The login DTO containing user credentials
	 * @param alreadyVerified Whether the user is already verified
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the ProfileServiceDTO with login information
	 * 
	 * Note: ProfileServiceException is handled within the reactive chain
	 */
	Uni<ProfileServiceDTO<?>> loginUser(Mutiny.Session session, UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Verifies if a username exists in the system
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param userLoginDTO The login DTO containing the username to verify
	 * @param system The system requesting the operation
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting a Boolean indicating whether the username exists
	 */
	Uni<Boolean> verifyUsernameExists(Mutiny.Session session, UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, java.util.UUID... identityToken);
	
	/**
	 * Verifies the password for a user
	 *
	 * @param session The Mutiny.Session to use for database operations
	 * @param userLoginDTO The login DTO containing the username and password to verify
	 * @param enterprise The enterprise context
	 * @param identityToken The identity tokens for security
	 * @return A Uni emitting the UserLoginDTO with verification information
	 */
	Uni<UserLoginDTO<?>> verifyPasswordForUser(Mutiny.Session session, UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, ISystems<?,?>system, java.util.UUID... identityToken);
}