package com.guicedee.activitymaster.sessions;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.fsdm.client.services.*;
import com.guicedee.activitymaster.fsdm.client.services.annotations.*;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.address.IAddress;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.classifications.IClassification;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedPartyType;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.InvolvedPartyException;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.SecurityAccessException;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.profiles.dto.UserDTO;
import com.guicedee.activitymaster.profiles.exceptions.*;
import com.guicedee.activitymaster.profiles.services.interfaces.IRolesService;
import com.guicedee.activitymaster.profiles.webdto.UserRegistrationDTO;
import com.guicedee.activitymaster.sessions.services.*;
import com.guicedee.activitymaster.sessions.services.dto.*;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.guicedinjection.json.LocalDateTimeDeserializer;
import com.guicedee.guicedinjection.pairing.Pair;
import com.guicedee.guicedservlets.GuicedServletKeys;
import com.guicedee.logger.LogFactory;
import jakarta.servlet.http.HttpServletRequest;
import net.sf.uadetector.ReadableUserAgent;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guicedee.activitymaster.fsdm.client.services.annotations.EventAction.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.DefaultClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.InvolvedPartyClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.address.AddressLocalSystemClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.address.AddressRemoteSystemClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.address.AddressWebClassifications.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.types.IPTypes.*;
import static com.guicedee.activitymaster.fsdm.client.services.classifications.types.IdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileClassifications.*;
import static com.guicedee.activitymaster.profiles.enumerations.ProfileIdentificationTypes.*;
import static com.guicedee.activitymaster.profiles.enumerations.SiteClientClassifications.*;
import static com.guicedee.activitymaster.profiles.services.enumerations.UserRoles.*;
import static com.guicedee.activitymaster.profiles.services.interfaces.IRolesService.*;
import static com.guicedee.activitymaster.sessions.services.ISessionMasterService.*;
import static com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications.*;
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
	private IPasswordsService<?> passwordsService;
	
	@Inject
	@Named(SessionMasterSystemName)
	private Provider<ISystems<?, ?>> system;
	
	@Inject
	@Named(SessionMasterSystemName)
	private UUID identityToken;
	
	@Inject
	private ProfileServiceDTO<?> dto;
	
	@Inject
	private Provider<ISession<?>> session;
	
	@Inject
	private Provider<UserSecurityDTO> us;
	
	@Override
	public ProfileServiceDTO<?> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();
		IInvolvedParty<?, ?> deviceIP = iInvolvedParty.builder()
		                                              .findByType(TypeDevice.toString(), dto.getWebClientUUID()
		                                                                                    .toString(),
				                                              system, identityToken)
		                                              .get()
		                                              .orElse(null);
		if (deviceIP == null)
		{
			deviceIP = createDeviceIP(profileServiceDTO);
			//new device there will be nothing else ever
			profileServiceDTO.setInvolvedParty(deviceIP);
			profileServiceDTO.setIdentityToken(deviceIP.getId());
			configureFromReadableUserAgent(deviceIP, GuiceContext.get(ReadableUserAgent.class), system, identityToken);
			try
			{
				HttpServletRequest request = get(GuicedServletKeys.getHttpServletRequestKey());
				configureFromHTTPServletRequest(profileServiceDTO, deviceIP, system, request, enterprise);
			}
			catch (Throwable T)
			{
				log.log(Level.FINER, "Unable to log servlet request information", T);
			}
		}
		//search by web client uuid
		IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
		if (foundIPCurrentOnDevice == null)
		{
			//new device there will be nothing else ever
			profileServiceDTO.setInvolvedParty(deviceIP);
			profileServiceDTO.setIdentityToken(deviceIP.getId());
			foundIPCurrentOnDevice = deviceIP;
		}
		else
		{
			profileServiceDTO.setInvolvedParty(foundIPCurrentOnDevice);
			profileServiceDTO.setIdentityToken(foundIPCurrentOnDevice.getId());
		}
		
		ISession<?> session = sessionMasterService.getSession(foundIPCurrentOnDevice, system, identityToken);
		UserSecurityDTO us = GuiceContext.get(UserSecurityDTO.class);
		
		updateLatestVisit(foundIPCurrentOnDevice, identityToken);
		Set<String> roles = new TreeSet<>();
		if (us.isLoggedIn())
		{
			if (!us.getLoginExpiresOn()
					.isBefore(com.entityassist.RootEntity.getNow()))
			{
				//fetch sync ip and compare for logout?
				
				return loginUser(new UserLoginDTO<>().setInvolvedParty(foundIPCurrentOnDevice)
				                                     .setWebClientUUID(profileServiceDTO.getWebClientUUID())
				                                     .setIdentityToken(foundIPCurrentOnDevice.getId()),
						true, system, identityToken);
			}
		}
		else
		{
			if (!foundIPCurrentOnDevice.equals(deviceIP))
			{
				logoutUser(profileServiceDTO, system, identityToken);
			}
			roles.addAll(Set.of(Visitor.toString()));
		}
		session.setSystem(system);
		session.addValue(USER_ROLES_SESSION_NAME, roles);
		session.addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		
		sessionMasterService.updateCache(foundIPCurrentOnDevice, session, system, identityToken);
		
		return profileServiceDTO;
	}
	
	private IInvolvedParty<?, ?> authenticate(UserLoginDTO<?> loginDTO)
	{
		return passwordsService.findByUsernameAndPassword(loginDTO.getUserName(),
				loginDTO.getPassword(),
				system.get(),
				true,
				identityToken);
	}
	
	@Override
	public ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, boolean alreadyVerified, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		IInvolvedParty<?, ?> deviceIP = involvedPartyService.get()
		                                                    .builder()
		                                                    .findByTypeAll(TypeDevice.toString(), dto.getWebClientUUID()
		                                                                                          .toString(),
				                                                    system, identityToken)
		                                                    .latestFirst()
		                                                    .setMaxResults(1)
		                                                    .get()
		                                                    .orElseThrow(() -> new InvolvedPartyException("Device IP must already exist before attempting to login"));
		try
		{
			IInvolvedParty<?, ?> foundParty;
			if (!alreadyVerified)
			{
				foundParty = authenticate(profileServiceDTO);
				profileServiceDTO.setPassword(null);
			}
			else
			{
				foundParty = profileServiceDTO.findInvolvedParty();
			}
			
			//move the webclient uuid to the new user, from device id, or wherever it is now
			//maybe do a if its not logged out check?
			String webClientID = profileServiceDTO.getWebClientUUID()
			                                      .toString();
			
			var involvedPartyIdentificationType
					= deviceIP.findInvolvedPartyIdentificationType(NoClassification.toString(), IdentificationTypeWebClientUUID.toString(), webClientID,
					system, true, true, identityToken);
			if (involvedPartyIdentificationType.isPresent())
			{
				involvedPartyIdentificationType.get()
				                               .archive(system, identityToken);
			}
			deviceIP.addChild(foundParty, DeviceUsedBy.toString(), webClientID, system, identityToken);
			foundParty.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(), IdentificationTypeWebClientUUID.toString(),
					webClientID, webClientID,
					system, identityToken);
			
			setUserLoggedIn(foundParty, profileServiceDTO, profileServiceDTO.isRememberMe(), system, this.identityToken);
		}
		catch (SecurityAccessException e)
		{
			throw new ProfileServiceException("Invalid username or password");
		}
		return profileServiceDTO;
	}
	
	
	@Override
	public ProfileServiceDTO<?> loginUser(UserLoginDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		return loginUser(profileServiceDTO, false, system, identityToken);
	}
	
	@Override
	public ProfileServiceDTO<?> logoutUser(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, UUID... identityToken) throws ProfileServiceException
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		var deviceIP
				= createDeviceIP(profileServiceDTO);
		
		var idWebClient
				= profileServiceDTO.findInvolvedParty()
				                   .findInvolvedPartyIdentificationType(NoClassification.toString(), IdentificationTypeWebClientUUID.toString(),
						                   profileServiceDTO.getWebClientUUID()
						                                    .toString(), system, false, false,
						                   this.identityToken);
		if (idWebClient.isPresent())
		{
			idWebClient.get()
			           .archive(system, identityToken);
			deviceIP.archiveChild(idWebClient.get()
			                                 .getPrimary(), DeviceUsedBy.toString(), null, system, identityToken);
		}
		
		String uuid = profileServiceDTO.getWebClientUUID()
		                               .toString();
		deviceIP.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(), IdentificationTypeWebClientUUID.toString(),
				uuid,
				uuid,
				system, identityToken);
		
		setUserLoggedOut(profileServiceDTO.findInvolvedParty(), deviceIP, profileServiceDTO, system, identityToken);
		
		return profileServiceDTO;
	}
	
	
	@InvolvedPartyEvent(Added)
	@LogItemEvent(Added)
	@Override
	public void setUserLoggedIn(@Party("UserLoggingIn") IInvolvedParty<?, ?> newIp,
	                            @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
	                            boolean rememberMe,
	                            @Party("SystemPerformed") ISystems<?, ?> system, UUID... identityToken)
	{
		if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
		{
			identityToken = new UUID[]{this.identityToken};
		}
		profileServiceDTO.setInvolvedParty(newIp);
		profileServiceDTO.setIdentityToken(newIp.getId());
		
		session.get().setInvolvedParty(newIp);
		ISession<?> session = sessionMasterService.getSession(newIp, system, identityToken);
		
		this.session.get().clear();
		this.session.get().updateFrom(session);

		dto.setEnterprise(enterprise);
		dto.setInvolvedParty(newIp);
		dto.setWebClientUUID(profileServiceDTO.getWebClientUUID());
		dto.setIdentityToken(profileServiceDTO.getIdentityToken());
		dto.setEnterprise(system.getEnterprise());
		
		dto.updateFrom(profileServiceDTO);
		
		this.session.get().addValue(IDENTITY_SESSION_NAME, dto);
		UserSecurityDTO us = GuiceContext.get(UserSecurityDTO.class);
		us.setLoggedIn(true)
		  .setLoginExpiresOn(rememberMe
				  ? LocalDateTime.MAX
				  : com.entityassist.RootEntity.getNow()
				                 .plusMinutes(20))
		  .setRememberMe(rememberMe);
		this.us.get().updateFrom(us);
		this.session.get().addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		this.session.get().addValue(USER_ROLES_SESSION_NAME, rolesService.getRoles(newIp, system, identityToken));
		
		sessionMasterService.updateSession(newIp, this.session.get(), system, identityToken);
	}
	
	@InvolvedPartyEvent(value = Added)
	@LogItemEvent(value = Added)
	@Override
	public void setUserLoggedOut(@Party("UserLoggedOut") IInvolvedParty<?, ?> involvedParty,
	                             @Party("DeviceUsedBy") IInvolvedParty<?, ?> deviceIP,
	                             @LogItem("SessionObject") ProfileServiceDTO<?> profileServiceDTO,
	                             @Party("SystemPerformed") ISystems<?, ?> system, UUID... identityToken)
	{
		us.get().setRememberMe(false);
		us.get().setLoggedIn(false);
		us.get().setLoginExpiresOn(com.entityassist.RootEntity.getNow());
		session.get().addValue(UserSecurityDTO.USER_SECURITY_SESSION_NAME, us);
		session.get().removeValue(USER_ROLES_SESSION_NAME);
		sessionMasterService.updateSession(involvedParty, session.get(), system, identityToken);
		
		//set profile service dto to the device IP
		profileServiceDTO.setIdentityToken(deviceIP.getId());
		profileServiceDTO.setInvolvedParty(deviceIP);
		ISession<?> session = sessionMasterService.getSession(deviceIP, system, identityToken);
		this.session.get().setInvolvedParty(deviceIP);
		this.session.get().setSystem(system);
		this.session.get().clear();
		this.session.get().updateFrom(session);
		
		sessionMasterService.updateSession(deviceIP, this.session.get(), system, identityToken);
	}
	
	@Override
	public UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException
	{
		IInvolvedParty<?, ?> ipExists = involvedPartyService.get()
		                                                    .builder()
		                                                    .findByIdentificationType(IdentificationTypeUserName.toString(), userRegistrationDTO.getUserName(), system, identityToken)
		                                                    .get()
		                                                    .orElse(null);
		if (ipExists != null)
		{
			if (ipExists.hasClassifications(ConfirmationKey, null, system, identityToken))
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
				system,
				this.identityToken);
		
		idType.expire(Duration.of(2, HOURS), identityToken);
		
		passwordsService.addUpdateUsernamePassword(userRegistrationDTO.getUserName(), userRegistrationDTO.getPassword(), newIp, system,
				this.identityToken);
		
		userRegistrationDTO.setPassword(null);
		var idUserNameType
				= newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),
				IdentificationTypeUserName,
				userRegistrationDTO.getUserName(),
				userRegistrationDTO.getUserName(),
				system,
				this.identityToken);
		idUserNameType.expire(Duration.of(2, HOURS), identityToken);
		
		Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification = newIp.findClassification(SecurityPassword, system, identityToken);
		Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification1 = newIp.findClassification(SecurityPasswordSalt, system, identityToken);
		
		classification.get()
		              .expire(Duration.of(2, HOURS), this.identityToken);
		classification1.get()
		               .expire(Duration.of(2, HOURS), this.identityToken);
		
		UserConfirmationKeyDTO<?> confirmationKeyDTO = new UserConfirmationKeyDTO<>()
				.setWebClientUUID(userRegistrationDTO.getWebClientUUID())
				.setIdentityToken(newIp.getId());
		confirmationKeyDTO.setConfirmationKey(UUID.randomUUID());
		IRelationshipValue<?, IClassification<?, ?>, ?> x =
				newIp.addOrUpdateClassification(ConfirmationKey, null, confirmationKeyDTO.getConfirmationKey()
				                                                                         .toString(), system, this.identityToken);
		
		x.expire(Duration.of(2, HOURS), this.identityToken);
		return confirmationKeyDTO;
	}
	
	
	IInvolvedParty<?, ?> createDeviceIP(ProfileServiceDTO<?> profileServiceDTO)
	{
		String webClientUUID = profileServiceDTO.getWebClientUUID()
		                                        .toString();
		IInvolvedPartyType<?, ?> deviceType = involvedPartyService.findType(TypeDevice.toString(), system.get(), identityToken);
		IInvolvedParty<?, ?> newIp = involvedPartyService.get()
		                                                 .builder()
		                                                 .findByTypeAll(TypeDevice.toString(),  webClientUUID, system.get(), identityToken)
		                                                 .latestFirst()
		                                                 .setMaxResults(1)
		                                                 .get()
		                                                 .orElse(null);
		if (newIp == null)
		{
			Pair<String, String> deviceIDType = new Pair<>();
			deviceIDType.setKey(IdentificationTypeWebClientUUID.toString())
			            .setValue(webClientUUID);
			newIp = involvedPartyService.create(system.get(), deviceIDType, false, identityToken);
			newIp.addOrReuseInvolvedPartyType(NoClassification.toString(), deviceType, webClientUUID, system.get(), identityToken);
		}
		return newIp;
	}
	
	IInvolvedParty<?, ?> updateLatestVisit(IInvolvedParty<?, ?> newIp,
	                                       UUID... identityToken)
	{
		String lastVisit = LocalDateTimeDeserializer.formats[0].format(com.entityassist.RootEntity.getNow());
		newIp.addOrUpdateClassification(LastVisitTime,
				null,
				lastVisit,
				system.get(),
				identityToken);
		return newIp;
	}
	
	
	@Override
	public boolean verifyUsernameExists(UserLoginDTO<?> userLoginDTO, ISystems<?, ?> system, UUID... identityToken)
	{
		if (Strings.isNullOrEmpty(userLoginDTO.getUserName()))
		{
			throw new ProfileServiceException("Username cannot be empty");
		}
		return passwordsService.doesUsernameExist(userLoginDTO.getUserName(), system);
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
		
		IInvolvedParty<?, ?> ip = passwordsService.findByUsernameAndPassword(userLoginDTO.getUserName(), userLoginDTO.getPassword(),
				system.get(), true, identityToken);
		userLoginDTO = new UserLoginDTO<>().setIdentityToken(ip.getId());
		return userLoginDTO;
	}
	
	
	IInvolvedParty<?, ?> configureFromHTTPServletRequest(UserDTO<?> dto, IInvolvedParty<?, ?> ip, ISystems<?, ?> profileSystem, HttpServletRequest servletRequest, IEnterprise<?, ?> enterprise)
	{
		StringBuilder sb = new StringBuilder();
		Enumeration<String> headerNames = servletRequest.getHeaderNames();
		while (headerNames.hasMoreElements())
		{
			String h = headerNames.nextElement();
			String v = servletRequest.getHeader(h);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(h, v);
			sb.append(jsonObject);
		}
		
		IAddressService<?> addressService = GuiceContext.get(IAddressService.class);
		String ipReal = servletRequest.getRemoteAddr();
		if (ipReal.equalsIgnoreCase("0:0:0:0:0:0:0:1"))
		{
			InetAddress inetAddress = null;
			try
			{
				inetAddress = InetAddress.getLocalHost();
			}
			catch (UnknownHostException e)
			{
				LogFactory.getLog("ConfigureFromServletRequest")
				          .log(Level.SEVERE, "Unknown host in getting INet Address for localhost ipv6", e);
			}
			String ipAddress = inetAddress.getHostAddress();
			ipReal = ipAddress;
		}
		IAddress<?, ?> ipAddress = addressService.addOrFindIPAddress(ipReal, profileSystem, identityToken);
		ip.addOrReuseAddress(ipAddress, RemoteAddressIPAddress.toString(), ipReal, ipReal, profileSystem, identityToken);
		IAddress<?, ?> hostName = addressService.addOrFindHostName(servletRequest.getRemoteHost(), profileSystem, identityToken);
		ip.addOrReuseAddress(hostName, RemoteAddressHostName.toString(), servletRequest.getRemoteHost(), servletRequest.getRemoteHost(), profileSystem, identityToken);
		IAddress<?, ?> localIpAddress = addressService.addOrFindHostName(servletRequest.getLocalAddr(), profileSystem, identityToken);
		ip.addOrReuseAddress(localIpAddress, LocalAddressIPAddress.toString(), servletRequest.getLocalAddr(), servletRequest.getLocalAddr(), profileSystem, identityToken);
		IAddress<?, ?> localHostName = addressService.addOrFindHostName(servletRequest.getLocalName(), profileSystem, identityToken);
		ip.addOrReuseAddress(localHostName, LocalAddressHostName.toString(), servletRequest.getLocalName(), servletRequest.getLocalName(), profileSystem, identityToken);
		IAddress<?, ?> webAddress = addressService.addOrFindWebAddress(servletRequest.getRequestURL()
		                                                                             .toString(), profileSystem, identityToken);
		ip.addOrReuseAddress(webAddress, WebAddress.toString(), servletRequest.getRequestURL()
		                                                                      .toString(), servletRequest.getRequestURL()
		                                                                                                 .toString(), profileSystem, identityToken);
		return ip;
	}
	
	IInvolvedParty<?, ?> configureFromReadableUserAgent(IInvolvedParty<?, ?> ip, ReadableUserAgent readableUserAgent, ISystems<?, ?> profileSystem, UUID... identityToken)
	{
		ip.addOrReuseClassification(BrowserDeviceCategory, readableUserAgent.getDeviceCategory()
		                                                                    .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(BrowserDevice, readableUserAgent.getDeviceCategory()
		                                                            .getCategory()
		                                                            .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(OperatingSystem, readableUserAgent.getOperatingSystem()
		                                                              .getName(), profileSystem, identityToken);
		ip.addOrReuseClassification(OperatingSystemFamily, readableUserAgent.getOperatingSystem()
		                                                                    .getFamily()
		                                                                    .getName(), profileSystem, identityToken);
		return ip;
	}
	
}
