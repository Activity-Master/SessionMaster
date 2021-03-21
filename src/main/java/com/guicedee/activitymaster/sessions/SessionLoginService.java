package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.client.services.*;
import com.guicedee.activitymaster.client.services.builders.warehouse.classifications.IClassification;
import com.guicedee.activitymaster.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.client.services.builders.warehouse.security.ISecurityToken;
import com.guicedee.activitymaster.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.client.services.exceptions.SecurityAccessException;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.dto.UserDTO;
import com.guicedee.activitymaster.profiles.events.visits.*;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.services.interfaces.IRolesService;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.*;
import com.guicedee.guicedinjection.interfaces.JobService;
import com.guicedee.guicedinjection.pairing.Pair;
import com.guicedee.guicedservlets.GuicedServletKeys;
import jakarta.servlet.http.HttpServletRequest;
import net.sf.uadetector.ReadableUserAgent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guicedee.activitymaster.client.services.classifications.DefaultClassifications.*;
import static com.guicedee.activitymaster.client.services.classifications.InvolvedPartyClassifications.*;
import static com.guicedee.activitymaster.client.services.classifications.SecurityTokenClassifications.*;
import static com.guicedee.activitymaster.client.services.classifications.types.IdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileIdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.services.enumerations.UserRoles.*;
import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static java.time.temporal.ChronoUnit.*;

public class SessionLoginService implements ISessionLoginService<SessionLoginService>
{
	private static final Logger log = Logger.getLogger(SessionLoginService.class.getName());
	
	@Inject
	private IEnterprise<?, ?> enterprise;
	
	@Inject
	private ISessionMasterService<?> sessionMasterService;
	
	@Inject
	private ISecurityTokenService<?> securityTokenService;
	
	@Inject
	private IRolesService<?> rolesService;
	
	@Inject
	private IInvolvedPartyService<?> involvedPartyService;
	
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?, ?> sessionMasterSystem;
	
	@Inject
	@Named(SessionMasterSystemName)
	private UUID sessionMasterSystemUUID;
	
	@Inject
	private ProfileServiceDTO<?> dto;
	
	@Inject
	private ISession<?> session;
	
	@Inject
	private UserSecurityDTO us;
	
	@Override
	public UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException
	{
		//	IEvent<?,?> registerEvent = get(IEventService.class)
		//			.createEvent(UserRegistered, sessionMasterSystem, sessionMasterSystemUUID);
		
		IInvolvedParty<?, ?> ipExists = involvedPartyService.findByIdentificationType(IdentificationTypeUserName.toString(), userRegistrationDTO.getUserName(), sessionMasterSystem, sessionMasterSystemUUID);
		
		if (ipExists != null)
		{
			if (ipExists.hasClassifications(ConfirmationKey, null, sessionMasterSystem, identityToken))
			{
				throw new WaitingForConfirmationKeyException("The email address is waiting for a confirmation key");
			}
			throw new UserExistsException("That email address is already in use as a valid identifier");
		}
		//ActivityMasterConfiguration.get().setSecurityEnabled(false);
		IInvolvedParty<?, ?> newIp;
		newIp = dto.findInvolvedParty();// involvedPartyService.findByIdentificationType(IdentificationTypeWebClientUUID, userRegistrationDTO.getWebClientUUID()
		//                                                                        .toString(), sessionMasterSystem, SessionMasterSystemUUID);
		//ActivityMasterConfiguration.get().setSecurityEnabled(true);
		var idType
				= newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),
				IdentificationTypeUserName,
				userRegistrationDTO.getUserName(),
				userRegistrationDTO.getUserName(),
				sessionMasterSystem,
				sessionMasterSystemUUID);
		
		idType.expire(Duration.of(2, HOURS),identityToken);
		
		involvedPartyService.addUpdateUsernamePassword(userRegistrationDTO.getUserName(), userRegistrationDTO.getPassword(), newIp, sessionMasterSystem,
				sessionMasterSystemUUID);
		
		userRegistrationDTO.setPassword(null);
		var idUserNameType
				= newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),
				IdentificationTypeUserName,
				userRegistrationDTO.getUserName(),
				userRegistrationDTO.getUserName(),
				sessionMasterSystem,
				sessionMasterSystemUUID);
		idUserNameType.expire(Duration.of(2, HOURS),identityToken);
		
		Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification = newIp.findClassification(SecurityPassword, system, identityToken);
		Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification1 = newIp.findClassification(SecurityPasswordSalt, system, identityToken);
		
		classification.get().expire(Duration.of(2, HOURS), sessionMasterSystemUUID);
		classification1.get().expire(Duration.of(2, HOURS), sessionMasterSystemUUID);
		
		UserConfirmationKeyDTO<?> confirmationKeyDTO = new UserConfirmationKeyDTO<>()
				.setWebClientUUID(userRegistrationDTO.getWebClientUUID())
				.setIdentityToken(newIp.getId());
		confirmationKeyDTO.setConfirmationKey(UUID.randomUUID());
		IRelationshipValue<?, IClassification<?, ?>, ?> x =
				newIp.addOrUpdateClassification(ConfirmationKey, null, confirmationKeyDTO.getConfirmationKey().toString(), sessionMasterSystem, sessionMasterSystemUUID);
		
		x.expire(Duration.of(2, HOURS), sessionMasterSystemUUID);
		
		//	registerEvent.addInvolvedParty(ConfirmationKey, sessionMasterSystem, confirmationKeyDTO.getConfirmationKey()
		//	                                                                                       .toString(), sessionMasterSystemUUID);
		
		return confirmationKeyDTO;
	}
	
	
	@Override
	public ProfileServiceDTO<?> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{sessionMasterSystemUUID};
		}
		us.setRememberMe(false);
		us.setLoggedIn(false);
		us.setLoginExpiresOn(LocalDateTime.now());
		session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		session.removeValue("user-roles");
		return profileServiceDTO;
	}
	
	@Override
	public ProfileServiceDTO<?> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{sessionMasterSystemUUID};
		}
		IInvolvedParty<?, ?> guestExists = dto.findInvolvedParty();// involvedPartyService.findByIdentificationType(IdentificationTypeWebClientUUID, stringStringMap.get(StaticStrings.LOCAL_STORAGE_PARAMETER_KEY), sessionMasterSystem, identityToken);
		final UUID[] identityToken1Final = identityToken;
		//	IEventService<?> eventService = get(IEventService.class);
		//	IEvent<?,?> event = eventService.createEvent(ProfileEventTypes.SiteVisit, sessionMasterSystem, sessionMasterSystemUUID);
		
		IInvolvedParty<?, ?> newIp = null;
		if (guestExists == null)
		{
			newIp = createNewVisitor(profileServiceDTO, enterprise, sessionMasterSystem, sessionMasterSystemUUID);
		}
		else
		{
			//Otherwise guess
			if (profileServiceDTO instanceof UserLoginDTO)
			{
				UserLoginDTO<?> userDTO = (UserLoginDTO<?>) profileServiceDTO;
				newIp = involvedPartyService.findByUsernameAndPassword(userDTO.getUserName()
						, userDTO.getPassword()
						, sessionMasterSystem
						, true
						, identityToken);
			}
			else
			{
				newIp = guestExists;
			}
		}
		
		session.setInvolvedParty(newIp);
		sessionMasterService.updateSession(newIp, session, sessionMasterSystem, sessionMasterSystemUUID);
		newIp = updateLatestVisit(profileServiceDTO, enterprise, newIp, identityToken);
		try
		{
			HttpServletRequest request = get(GuicedServletKeys.getHttpServletRequestKey());
			newIp = configureFromHTTPServletRequest(profileServiceDTO, newIp, sessionMasterSystem, request, enterprise);
			configureFromReadableUserAgent(profileServiceDTO, newIp, get(ReadableUserAgent.class), sessionMasterSystem, enterprise, identityToken1Final);
		}
		catch (Throwable T)
		{
			log.log(Level.FINER, "Unable to log servlet request information", T);
		}
		
		if (guestExists != null && !guestExists.equals(newIp))
		{
			var idWebClient=
					guestExists.findInvolvedPartyIdentificationType(NoClassification,IdentificationTypeWebClientUUID,null, sessionMasterSystem,true,true, sessionMasterSystemUUID);
			if (idWebClient.isPresent())
			{
				idWebClient.get()
				           .expire();
				newIp.addInvolvedPartyIdentificationType(NoClassification.toString(),IdentificationTypeWebClientUUID.toString(), idWebClient.get()
				                                                                                     .getValue(), sessionMasterSystem, sessionMasterSystemUUID);
			}
		}
		profileServiceDTO.setInvolvedParty(newIp);
		session.setSystem(system);
		session.setInvolvedParty(newIp);
		
		Set<String> roles = new TreeSet<>();
		session.setInvolvedParty(newIp);
		/*UserSecurityDTO us;
		if (session.hasValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME))
		{
			us = session.as(UserSecurityDTO.USER_SECURITY_SESSION_NAME, UserSecurityDTO.class);
		}
		else
		{
			us = new UserSecurityDTO();
			session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		}*/
		
		if (us.isLoggedIn())
		{
			//setUserLoggedIn(newIp, profileServiceDTO, us.isRememberMe(), system, identityToken);
			roles.addAll(rolesService.getRoles(session.getInvolvedParty(), sessionMasterSystem, sessionMasterSystemUUID));
		}
		else
		{
			roles.addAll(Set.of(Visitor.toString()));
		}
		
		session.setSystem(system);
		session.addValue("user-roles", roles);
		session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		sessionMasterService.updateSession(newIp, session, sessionMasterSystem, sessionMasterSystemUUID);
		
		return profileServiceDTO;
	}
	
	IInvolvedParty<?, ?> createNewVisitor(ProfileServiceDTO<?> profileServiceDTO, IEnterprise<?, ?> enterprise, ISystems<?, ?> sessionMasterSystem, UUID... identityToken)
	{
		IInvolvedParty<?, ?> newIp;
		//Create new guest record
		Pair<String, String> guestIDType = new Pair<>();
		guestIDType.setKey(IdentificationTypeWebClientUUID.toString())
		           .setValue(profileServiceDTO.getWebClientUUID()
		                                      .toString());
		
		newIp = involvedPartyService.create(sessionMasterSystem, guestIDType, true, identityToken);
		
		ISecurityToken<?, ?> visitorsGroup = securityTokenService.getVisitorsGuestsFolder(sessionMasterSystem, identityToken);
		
		ISecurityToken<?, ?> myToken = securityTokenService.create(Identity.toString(),
				profileServiceDTO.getWebClientUUID()
				                 .toString(),
				"A new visitor device",
				sessionMasterSystem,
				visitorsGroup,
				identityToken);
		newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),IdentificationTypeUUID.toString(),myToken.getSecurityToken(),
				myToken.getSecurityToken(), sessionMasterSystem, identityToken);
		
		profileServiceDTO.setIdentityToken(newIp.getId());
		
	/*	UpdateNewVisitEvent visitEvent = get(UpdateNewVisitEvent.class);
		visitEvent.setEnterprise(enterprise)
		          .setProfileServiceDTO(profileServiceDTO)
		          .setIdentityToken(new UUID[]{newIp.getId()})
		          .setNewIp(newIp);*/
		
		
		rolesService.addRole(newIp, Visitor.toString(), profileServiceDTO, sessionMasterSystem, sessionMasterSystemUUID);
		
	/*	JobService.getInstance()
		          .addJob(UpdateNewVisitEvent.getJobServiceName(), visitEvent);*/
		
		return newIp;
	}
	
	IInvolvedParty<?, ?> updateLatestVisit(ProfileServiceDTO<?> profileServiceDTO, IEnterprise<?, ?> enterprise, IInvolvedParty<?, ?> newIp,
	                                       UUID... identityToken)
	{
		UpdateLastVisitEvent req = get(UpdateLastVisitEvent.class);
		req
				//.setEvent(event)
				.setProfileServiceDTO(profileServiceDTO)
				.setEnterprise(enterprise)
				.setNewIp(newIp)
				.setIdentityToken(identityToken);
		
		JobService.getInstance()
		          .addJob(UpdateLastVisitEvent.getJobServiceName(), req);
		
		return newIp;
	}
	
	
	@Override
	public ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{sessionMasterSystemUUID};
		}
		IInvolvedParty<?, ?> currentIp = session.getInvolvedParty();
		
		IInvolvedParty<?, ?> newIp = null;
		try
		{
			IInvolvedParty<?, ?> foundParty = involvedPartyService.findByUsernameAndPassword(profileServiceDTO.getUserName(),
					profileServiceDTO.getPassword(),
					sessionMasterSystem,
					true,
					sessionMasterSystemUUID);
			profileServiceDTO.setIdentityToken(foundParty.getId());
			newIp = foundParty;
			
			if (currentIp != null && !currentIp.equals(newIp))
			{
				var idWebClient
						= currentIp.findInvolvedPartyIdentificationType(NoClassification.toString(),IdentificationTypeWebClientUUID.toString(), null,sessionMasterSystem,true,true,
						sessionMasterSystemUUID);
				
				if (idWebClient.isPresent() &&
				    !currentIp.getId()
				              .equals(newIp.getId()))
				{
					sessionMasterService.expireSession(currentIp, session, system, sessionMasterSystemUUID);
					currentIp.moveWebClientUUIDToNewInvolvedParty(newIp,
							idWebClient.get()
							           .getValueAsUUID());
				}
			}
			setUserLoggedIn(newIp, profileServiceDTO, profileServiceDTO.isRememberMe(), system, sessionMasterSystemUUID);
			
		}
		catch (SecurityAccessException e)
		{
			throw new ProfileServiceException("Invalid username or password");
		}
		profileServiceDTO.setPassword(null);
		return profileServiceDTO;
	}
	
	private void setUserLoggedIn(IInvolvedParty<?, ?> newIp, ProfileServiceDTO<?> profileServiceDTO, boolean rememberMe, ISystems<?, ?> system, UUID... identityToken)
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{sessionMasterSystemUUID};
		}
		session.setInvolvedParty(newIp);
		
		dto.setWebClientUUID(profileServiceDTO.getWebClientUUID());
		dto.setIdentityToken(profileServiceDTO.getIdentityToken());
		dto.setEnterprise(system.getEnterprise());
		session.addValue(IDENTITY_SESSION_NAME, dto);
		
		/*UserSecurityDTO us = null;
		if (session.hasValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME))
		{
			us = session.as(UserSecurityDTO.USER_SECURITY_SESSION_NAME, UserSecurityDTO.class);
		}
		else
		{
			session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us = new UserSecurityDTO());
		}*/
		us = us.setLoggedIn(true)
		       .setLoginExpiresOn(rememberMe
				       ? LocalDateTime.MAX
				       : LocalDateTime.now()
				                      .plusMinutes(20))
		       .setRememberMe(rememberMe);
		session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		Set<String> roles = rolesService.getRoles(newIp, sessionMasterSystem, sessionMasterSystemUUID);
		session.addValue("user-roles", roles);
		
		dto.setEnterprise(enterprise);
		dto.setInvolvedParty(newIp);
	}
	
	
	@Override
	public boolean verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, UUID... identityToken)
	{
		if (Strings.isNullOrEmpty(userLoginDTO.getUserName()))
		{
			throw new ProfileServiceException("Username cannot be empty");
		}
		return involvedPartyService.doesUsernameExist(userLoginDTO.getUserName(), system);
	}
	
	@Override
	public UserLoginDTO<?> verifyPasswordForUser(UserLoginDTO<?> userLoginDTO, IEnterprise<?, ?> enterprise, UUID... identityToken)
	{
		if (Objects.isNull(userLoginDTO.getIdentityToken()))
		{
			throw new ProfileServiceException("User Login DTO Already needs to have an associated UUID to login with a password");
		}
		if (Strings.isNullOrEmpty(userLoginDTO.getPassword()))
		{
			throw new ProfileServiceException("Passwords cannot be empty");
		}
		
		IInvolvedParty<?, ?> ip = involvedPartyService.findByUsernameAndPassword(userLoginDTO.getUserName(), userLoginDTO.getPassword(), sessionMasterSystem, true, identityToken);
		userLoginDTO = new UserLoginDTO<>().setIdentityToken(ip.getId());
		return userLoginDTO;
	}
	
	
	IInvolvedParty<?, ?> configureFromHTTPServletRequest(UserDTO<?> dto, IInvolvedParty<?, ?> ip, ISystems<?, ?> profileSystem, HttpServletRequest servletRequest, IEnterprise<?, ?> enterprise)
	{
		ConfigureFromServletRequestEvent req = get(ConfigureFromServletRequestEvent.class);
		req
				//.setEvent(event)
				.setDto(dto)
				.setIp(ip)
				.setProfileSystem(profileSystem)
				.setServletRequest(servletRequest)
				.setEnterprise(enterprise);
		JobService.getInstance()
		          .addJob(ConfigureFromServletRequestEvent.getJobServiceName(), req);
		
		return ip;
	}
	
	IInvolvedParty<?, ?> configureFromReadableUserAgent(UserDTO<?> dto, IInvolvedParty<?, ?> ip, ReadableUserAgent readableUserAgent, ISystems<?, ?> profileSystem, IEnterprise<?, ?> enterprise, UUID... identityToken)
	{
		ConfigureFromReadableUserAgentEvent ev = get(ConfigureFromReadableUserAgentEvent.class);
		ev.setEnterprise(enterprise)
		  //  .setEvent(event)
		  .setDto(dto)
		  .setIdentityToken(identityToken)
		  .setIp(ip)
		  .setReadableUserAgent(readableUserAgent)
		  .setProfileSystem(profileSystem);
		
		JobService.getInstance()
		          .addJob(ConfigureFromReadableUserAgentEvent.getJobServiceName(), ev);
		
		return ip;
	}
	
	
}
