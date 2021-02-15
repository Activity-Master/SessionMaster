package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.core.services.enumtypes.IIdentificationType;
import com.guicedee.activitymaster.core.services.exceptions.SecurityAccessException;
import com.guicedee.activitymaster.core.services.security.Passwords;
import com.guicedee.activitymaster.core.services.system.*;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.dto.UserDTO;
import com.guicedee.activitymaster.profiles.enumerations.ProfileEventTypes;
import com.guicedee.activitymaster.profiles.events.UpdateNewVisitEvent;
import com.guicedee.activitymaster.profiles.events.visits.*;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.services.interfaces.IRolesService;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.*;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedinjection.interfaces.JobService;
import com.guicedee.guicedinjection.pairing.Pair;
import com.guicedee.guicedservlets.GuicedServletKeys;
import com.jwebmp.core.base.ajax.AjaxCall;
import com.jwebmp.core.utilities.StaticStrings;
import jakarta.servlet.http.HttpServletRequest;
import net.sf.uadetector.ReadableUserAgent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guicedee.activitymaster.core.services.classifications.classification.Classifications.*;
import static com.guicedee.activitymaster.core.services.classifications.involvedparty.InvolvedPartyClassifications.*;
import static com.guicedee.activitymaster.core.services.classifications.securitytokens.SecurityTokenClassifications.*;
import static com.guicedee.activitymaster.core.services.types.IdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileEventTypes.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileIdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.services.enumerations.UserRoles.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static java.time.temporal.ChronoUnit.*;

public class SessionLoginService implements ISessionLoginService<SessionLoginService>
{
	private static final Logger log = Logger.getLogger(SessionLoginService.class.getName());
	
	@Override
	public UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?> system, UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException
	{
		IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IEnterprise<?> enterprise = system.getEnterprise();
		ISystems<?> SessionMasterSystem = get(SessionMasterSystem.class)
				.getSystem(enterprise);
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		IEvent<?> registerEvent = get(IEventService.class)
				.createEvent(UserRegistered, SessionMasterSystem, SessionMasterSystemUUID);
		
		IInvolvedParty<?> ipExists = involvedPartyService.findByIdentificationType(IdentificationTypeEmailAddress,
				new Passwords().integerEncrypt(userRegistrationDTO.getUserName()
				                                                  .getBytes())
				, SessionMasterSystem, SessionMasterSystemUUID);
		if (ipExists != null)
		{
			if (ipExists.hasClassifications(ConfirmationKey, SessionMasterSystem, identityToken))
			{
				throw new WaitingForConfirmationKeyException("The email address is waiting for a confirmation key");
			}
			throw new UserExistsException("That email address is already in use as a valid identifier");
		}
		//ActivityMasterConfiguration.get().setSecurityEnabled(false);
		IInvolvedParty<?> newIp;
		newIp = involvedPartyService.findByIdentificationType(IdentificationTypeWebClientUUID, userRegistrationDTO.getWebClientUUID()
		                                                                                                          .toString(), SessionMasterSystem, SessionMasterSystemUUID);
		//ActivityMasterConfiguration.get().setSecurityEnabled(true);
		var idType
				= newIp.addOrUpdateIdentificationType(IdentificationTypeEmailAddress,
				NoClassification.classificationName(),
				new Passwords().integerEncrypt(userRegistrationDTO.getUserName()
				                                                  .getBytes()),
				SessionMasterSystem,
				SessionMasterSystemUUID);
		
		newIp.expireIdentificationType(idType, Duration.of(2, HOURS));
		
		involvedPartyService.addUpdateUsernamePassword(registerEvent, userRegistrationDTO.getUserName(), userRegistrationDTO.getPassword(), newIp, SessionMasterSystem,
				SessionMasterSystemUUID);
		
		userRegistrationDTO.setPassword(null);
		var idUserNameType
				= newIp.addOrUpdateIdentificationType(IdentificationTypeUserName,
				NoClassification.classificationName(),
				new Passwords().integerEncrypt(userRegistrationDTO.getUserName()
				                                                  .getBytes()),
				SessionMasterSystem,
				SessionMasterSystemUUID);
		newIp.expireIdentificationType(idUserNameType, Duration.of(2, HOURS));
		
		newIp.expire(SecurityPassword, Duration.of(2, HOURS), SessionMasterSystem, SessionMasterSystemUUID);
		newIp.expire(SecurityPasswordSalt, Duration.of(2, HOURS), SessionMasterSystem, SessionMasterSystemUUID);
		
		UserConfirmationKeyDTO confirmationKeyDTO = (UserConfirmationKeyDTO) new UserConfirmationKeyDTO()
				.setWebClientUUID(userRegistrationDTO.getWebClientUUID())
				.setIdentityToken(newIp.getId());
		confirmationKeyDTO.setConfirmationKey(UUID.randomUUID());
		IRelationshipValue<IInvolvedParty<?>, IClassification<?>, ?> x = newIp.addOrUpdate(ConfirmationKey, null, confirmationKeyDTO.getConfirmationKey()
		                                                                                                                            .toString(), SessionMasterSystem, SessionMasterSystemUUID);
		
		x.expire(Duration.of(2, HOURS), SessionMasterSystem, SessionMasterSystemUUID);
		registerEvent.addInvolvedParty(ConfirmationKey, SessionMasterSystem, confirmationKeyDTO.getConfirmationKey()
		                                                                                       .toString(), SessionMasterSystemUUID);
		
		return confirmationKeyDTO;
	}
	
	
	@Override
	public ProfileServiceDTO<?> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?> system, UUID... identityToken) throws ProfileServiceException
	{
		//	IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IEnterprise<?> enterprise = system.getEnterprise();
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{SessionMasterSystemUUID};
		}
		
		//		IInvolvedParty<?> newIp = involvedPartyService.findByIdentificationType(IdentificationTypeWebClientUUID, profileServiceDTO.getWebClientUUID()
		//		                                                                                                                          .toString(), SessionMasterSystem, SessionMasterSystemUUID);
		ISession<?> iSess = get(ISession.class);
		UserSecurityDTO us = iSess.as("user-security", UserSecurityDTO.class);
		us.setRememberMe(false);
		us.setLoggedIn(false);
		us.setLoginExpiresOn(LocalDateTime.now());
		iSess.addValue("user-security", us);
		iSess.removeValue("user-roles");
		return profileServiceDTO;
	}
	
	@Override
	public ProfileServiceDTO<?> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?> system, UUID... identityToken) throws ProfileServiceException
	{
		IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IEnterprise<?> enterprise = system.getEnterprise();
		
		ISystems<?> SessionMasterSystem = get(SessionMasterSystem.class)
				.getSystem(enterprise);
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{SessionMasterSystemUUID};
		}
		
		AjaxCall<?> ajaxCall = get(AjaxCall.class);
		Map<String, String> stringStringMap = ajaxCall.getVariable(StaticStrings.LOCAL_STORAGE_VARIABLE_KEY)
		                                              .asMap();
		
		IInvolvedParty<?> guestExists = involvedPartyService.findByIdentificationType(IdentificationTypeWebClientUUID, stringStringMap.get(StaticStrings.LOCAL_STORAGE_PARAMETER_KEY), SessionMasterSystem, identityToken);
		final UUID[] identityToken1Final = identityToken;
		IEventService<?> eventService = get(IEventService.class);
		IEvent<?> event = eventService.createEvent(ProfileEventTypes.SiteVisit, SessionMasterSystem, SessionMasterSystemUUID);
		IInvolvedParty<?> newIp = null;
		if (guestExists == null)
		{
			newIp = createNewVisitor(event, profileServiceDTO, enterprise, SessionMasterSystem, SessionMasterSystemUUID);
		}
		else
		{
			//Otherwise guess
			if (profileServiceDTO instanceof UserLoginDTO)
			{
				UserLoginDTO<?> userDTO = (UserLoginDTO<?>) profileServiceDTO;
				newIp = involvedPartyService.findByUsernameAndPassword(userDTO.getUserName()
						, userDTO.getPassword()
						, SessionMasterSystem
						, true
						, identityToken);
			}
			else
			{
				newIp = guestExists;
			}
		}
		
		ISessionMasterService<?> sessionMasterService = get(ISessionMasterService.class);
		ISession<?> session = null;
		session = sessionMasterService.getSession(newIp, SessionMasterSystem, SessionMasterSystemUUID);
		
		
		session.setInvolvedParty(newIp);
		sessionMasterService.updateSession(newIp, session, SessionMasterSystem, SessionMasterSystemUUID);
		newIp = updateLatestVisit(event, profileServiceDTO, enterprise, newIp, identityToken);
		try
		{
			HttpServletRequest request = get(GuicedServletKeys.getHttpServletRequestKey());
			newIp = configureFromHTTPServletRequest(event, profileServiceDTO, newIp, SessionMasterSystem, request, enterprise);
			configureFromReadableUserAgent(event, profileServiceDTO, newIp, get(ReadableUserAgent.class), SessionMasterSystem, enterprise, identityToken1Final);
		}
		catch (Throwable T)
		{
			log.log(Level.FINER, "Unable to log servlet request information", T);
		}
		
		if (guestExists != null && !guestExists.equals(newIp))
		{
			Optional<IRelationshipValue<IInvolvedParty<?>, IInvolvedPartyIdentificationType<?>, ?>> idWebClient = guestExists.findIdentificationType(IdentificationTypeWebClientUUID, SessionMasterSystem,
					SessionMasterSystemUUID);
			if (idWebClient.isPresent())
			{
				idWebClient.get()
				           .expire();
				newIp.addIdentificationType(IdentificationTypeWebClientUUID, idWebClient.get()
				                                                                        .getValue(), SessionMasterSystem, SessionMasterSystemUUID);
			}
		}
		profileServiceDTO.setInvolvedParty(newIp);
		session.setSystem(system);
		session.setInvolvedParty(newIp);
		
		Set<String> roles = new TreeSet<>();
		session.setInvolvedParty(newIp);
		UserSecurityDTO us;
		if (session.hasValue("user-security"))
		{
			us = session.as("user-security", UserSecurityDTO.class);
		}
		else
		{
			us = new UserSecurityDTO();
			session.addValue("user-security", us);
		}
		
		if (us.isLoggedIn())
		{
			//setUserLoggedIn(newIp, profileServiceDTO, us.isRememberMe(), system, identityToken);
			IRolesService<?> rolesService = get(IRolesService.class);
			roles.addAll(rolesService.getRoles(session.getInvolvedParty(), SessionMasterSystem, SessionMasterSystemUUID));
		}
		else
		{
			roles.addAll(Set.of(Visitor.toString()));
		}
		session.setSystem(system);
		session.addValue("user-roles", roles);
		session.addValue("user-security", us);
		sessionMasterService.updateSession(newIp, session, SessionMasterSystem, SessionMasterSystemUUID);
		
		return profileServiceDTO;
	}
	
	IInvolvedParty<?> createNewVisitor(IEvent<?> event, ProfileServiceDTO<?> profileServiceDTO, IEnterprise<?> enterprise, ISystems<?> SessionMasterSystem, UUID... identityToken)
	{
		IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IInvolvedParty<?> newIp;
		//Create new guest record
		Pair<IIdentificationType<?>, String> guestIDType = new Pair<>();
		guestIDType.setKey(IdentificationTypeWebClientUUID)
		           .setValue(profileServiceDTO.getWebClientUUID()
		                                      .toString());
		
		newIp = involvedPartyService.create(SessionMasterSystem, guestIDType, true, identityToken);
		
		ISecurityToken<?> visitorsGroup = get(ISecurityTokenService.class)
				.getVisitorsGuestsFolder(SessionMasterSystem, identityToken);
		
		ISecurityToken<?> myToken = get(ISecurityTokenService.class).create(Identity,
				profileServiceDTO.getWebClientUUID()
				                 .toString(),
				"A new visitor device",
				SessionMasterSystem,
				visitorsGroup,
				identityToken);
		newIp.addOrUpdateIdentificationType(IdentificationTypeUUID, NoClassification.classificationName(), myToken.getSecurityToken(), SessionMasterSystem, identityToken);
		
		profileServiceDTO.setIdentityToken(newIp.getId());
		UpdateNewVisitEvent visitEvent = get(UpdateNewVisitEvent.class);
		visitEvent.setEnterprise(enterprise)
		          .setEvent(event)
		          .setProfileServiceDTO(profileServiceDTO)
		          .setIdentityToken(new UUID[]{newIp.getId()})
		          .setNewIp(newIp);
		
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		get(IRolesService.class).addRole(newIp, Visitor.toString(), profileServiceDTO, SessionMasterSystem, SessionMasterSystemUUID);
		
		JobService.getInstance()
		          .addJob(UpdateNewVisitEvent.getJobServiceName(), visitEvent);
		
		return newIp;
	}
	
	IInvolvedParty<?> updateLatestVisit(IEvent<?> event, ProfileServiceDTO<?> profileServiceDTO, IEnterprise<?> enterprise, IInvolvedParty<?> newIp,
	                                    UUID... identityToken)
	{
		UpdateLastVisitEvent req = get(UpdateLastVisitEvent.class);
		req.setEvent(event)
		   .setProfileServiceDTO(profileServiceDTO)
		   .setEnterprise(enterprise)
		   .setNewIp(newIp)
		   .setIdentityToken(identityToken);
		
		JobService.getInstance()
		          .addJob(UpdateLastVisitEvent.getJobServiceName(), req);
		
		return newIp;
	}
	
	
	@Override
	public ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?> system, UUID... identityToken) throws ProfileServiceException
	{
		IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IEnterprise<?> enterprise = system.getEnterprise();
		
		ISystems<?> SessionMasterSystem = get(SessionMasterSystem.class)
				.getSystem(enterprise);
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{SessionMasterSystemUUID};
		}
		IInvolvedParty<?> currentIp = get(ISession.class)
				.getInvolvedParty();
		IInvolvedParty<?> newIp = null;
		try
		{
			IInvolvedParty<?> foundParty = involvedPartyService.findByUsernameAndPassword(profileServiceDTO.getUserName(),
					profileServiceDTO.getPassword(),
					SessionMasterSystem,
					true,
					SessionMasterSystemUUID);
			profileServiceDTO.setIdentityToken(foundParty.getId());
			newIp = foundParty;
			
			if (currentIp != null && !currentIp.equals(newIp))
			{
				var idWebClient
						= currentIp.findIdentificationType(IdentificationTypeWebClientUUID, SessionMasterSystem,
						SessionMasterSystemUUID);
				
				if (idWebClient.isPresent() &&
				    !currentIp.getId()
				              .equals(newIp.getId()))
				{
					ISessionMasterService<?> sessionMasterService = get(ISessionMasterService.class);
					ISession<?> session = sessionMasterService.getSession(currentIp, SessionMasterSystem, SessionMasterSystemUUID);
					sessionMasterService.expireSession(currentIp, session, system, SessionMasterSystemUUID);
					currentIp.moveWebClientUUIDToNewInvolvedParty(newIp,
							idWebClient.get()
							           .getValueAsUUID());
				}
			}
			setUserLoggedIn(newIp, profileServiceDTO, profileServiceDTO.isRememberMe(), system, SessionMasterSystemUUID);
			
		}
		catch (SecurityAccessException e)
		{
			throw new ProfileServiceException("Invalid username or password");
		}
		profileServiceDTO.setPassword(null);
		return profileServiceDTO;
	}
	
	private void setUserLoggedIn(IInvolvedParty<?> newIp, ProfileServiceDTO<?> profileServiceDTO, boolean rememberMe, ISystems<?> system, UUID... identityToken)
	{
		IInvolvedPartyService<?> involvedPartyService = get(IInvolvedPartyService.class);
		IEnterprise<?> enterprise = system.getEnterprise();
		ISystems<?> SessionMasterSystem = get(SessionMasterSystem.class)
				.getSystem(enterprise);
		UUID SessionMasterSystemUUID = get(SessionMasterSystem.class)
				.getSystemToken(enterprise);
		
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{SessionMasterSystemUUID};
		}
		
		ISession<?> iSess = get(ISession.class);
		iSess.setInvolvedParty(newIp);
		
		ProfileServiceDTO<?> identityTokensDTO = new ProfileServiceDTO<>();
		identityTokensDTO.setWebClientUUID(profileServiceDTO.getWebClientUUID());
		identityTokensDTO.setIdentityToken(profileServiceDTO.getIdentityToken());
		identityTokensDTO.setEnterprise(system.getEnterprise());
		iSess.addValue("identity", identityTokensDTO);
		
		UserSecurityDTO us = null;
		if (iSess.hasValue("user-security"))
		{
			us = iSess.as("user-security", UserSecurityDTO.class);
		}
		else
		{
			iSess.addValue("user-security", us = new UserSecurityDTO());
		}
		us = us.setLoggedIn(true)
		       .setLoginExpiresOn(rememberMe
				       ? LocalDateTime.MAX
				       : LocalDateTime.now()
				                      .plusMinutes(20))
		       .setRememberMe(rememberMe);
		iSess.addValue("user-security", us);
		IRolesService<?> rolesService = get(IRolesService.class);
		Set<String> roles = rolesService.getRoles(newIp, SessionMasterSystem, SessionMasterSystemUUID);
		iSess.addValue("user-roles", roles);
		
		profileServiceDTO.setEnterprise(enterprise);
		profileServiceDTO.setInvolvedParty(newIp);
	}
	
	
	@Override
	public boolean verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?> system, UUID... identityToken)
	{
		IInvolvedPartyService<?> ips = GuiceContext.get(IInvolvedPartyService.class);
		if (Strings.isNullOrEmpty(userLoginDTO.getUserName()))
		{
			throw new ProfileServiceException("Username cannot be empty");
		}
		return ips.doesUsernameExist(userLoginDTO.getUserName(), system);
	}
	
	@Override
	public UserLoginDTO<?> verifyPasswordForUser(UserLoginDTO<?> userLoginDTO, IEnterprise<?> enterprise, UUID... identityToken)
	{
		IInvolvedPartyService<?> ips = GuiceContext.get(IInvolvedPartyService.class);
		if (Objects.isNull(userLoginDTO.getIdentityToken()))
		{
			throw new ProfileServiceException("User Login DTO Already needs to have an associated UUID to login with a password");
		}
		if (Strings.isNullOrEmpty(userLoginDTO.getPassword()))
		{
			throw new ProfileServiceException("Passwords cannot be empty");
		}
		ISystems<?> profileSystem = get(SessionMasterSystem.class)
				.getSystem(enterprise);
		IInvolvedParty<?> ip = ips.findByUsernameAndPassword(userLoginDTO.getUserName(), userLoginDTO.getPassword(), profileSystem, true, identityToken);
		userLoginDTO = (UserLoginDTO<?>) new UserLoginDTO<>().setIdentityToken(ip.getId());
		return userLoginDTO;
	}
	
	
	IInvolvedParty<?> configureFromHTTPServletRequest(IEvent<?> event, UserDTO<?> dto, IInvolvedParty<?> ip, ISystems<?> profileSystem, HttpServletRequest servletRequest, IEnterprise<?> enterprise)
	{
		ConfigureFromServletRequestEvent req = get(ConfigureFromServletRequestEvent.class);
		req.setEvent(event)
		   .setDto(dto)
		   .setIp(ip)
		   .setProfileSystem(profileSystem)
		   .setServletRequest(servletRequest)
		   .setEnterprise(enterprise);
		JobService.getInstance()
		          .addJob(ConfigureFromServletRequestEvent.getJobServiceName(), req);
		
		return ip;
	}
	
	IInvolvedParty<?> configureFromReadableUserAgent(IEvent<?> event, UserDTO<?> dto, IInvolvedParty<?> ip, ReadableUserAgent readableUserAgent, ISystems<?> profileSystem, IEnterprise<?> enterprise, UUID... identityToken)
	{
		ConfigureFromReadableUserAgentEvent ev = get(ConfigureFromReadableUserAgentEvent.class);
		ev.setEnterprise(enterprise)
		  .setEvent(event)
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
