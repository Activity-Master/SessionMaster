package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Singleton;
import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.core.services.security.Passwords;
import com.guicedee.activitymaster.core.services.system.IEnterpriseService;
import com.guicedee.activitymaster.core.services.system.IInvolvedPartyService;
import com.guicedee.activitymaster.sessions.implementations.AsyncSessionUpdate;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.logger.LogFactory;

import jakarta.cache.annotation.CacheKey;
import jakarta.cache.annotation.CacheResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.core.services.classifications.resourceitems.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

public class SessionMasterService
		implements ISessionMasterService<SessionMasterService>
{
	private final ObjectMapper mapper = GuiceContext.get(ObjectMapper.class);
	private final TypeFactory typeFactory = mapper.getTypeFactory();
	private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);
	
	@Override
	@CacheResult(cacheName = "SessionCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty, @CacheKey IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), enterpriseName, identityToken);
	}
	
	@Override
	@CacheResult(cacheName = "SessionCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> original, @CacheKey IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(enterpriseName);
		ISystems<?> sessionSystem = get(SessionMasterSystem.class).getSystem(enterprise);
		ISession<?> session = original;
		try
		{
			if (session == null && involvedParty == null)
			{
				LogFactory.getLog("SessionMasterService")
				          .warning("Session has no involved party. First session call?");
				return session;
			}
			
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
			IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = involvedParty.addOrReuseResourceItem(SessionClassifications.SessionObject,
			                                                                                                                JsonPacket,
			                                                                                                                null,
			                                                                                                                null,
			                                                                                                                sessionString.getBytes(),
			                                                                                                                "encrypted/json", sessionSystem, identityToken);
			byte[] storedSession = sessionObject.getSecondary()
			                                    .getData();
			sessionString = new String(new Passwords().integerDecrypt(new String(storedSession)));
			
			HashMap<String, String> returned = new HashMap<>();
			if (!(sessionString.isEmpty() || "{}".equals(sessionString)))
			{
				returned = get(DefaultObjectMapper).readValue(sessionString, mapType);
			}
			
			Session sess = (Session) session;
			sess.getValues()
			    .putAll(returned);
		}
		catch (IOException e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the inecoming object to retrieve a session", e);
		}
		if (session != null)
		{
			session.setEnterpriseName(enterpriseName);
			session.setInvolvedParty(involvedParty);
		}
		return session;
	}
	
	@Override
	@CacheResult(cacheName = "SessionCache",
	             skipGet = true)
	public ISession<?> updateSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> session, @CacheKey IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(session.getEnterpriseName());
		ISystems<?> sessionSystem = get(SessionMasterSystem.class).getSystem(enterprise);
		
		if (session == null || involvedParty == null || session.getInvolvedParty() == null)
		{
			if (session.hasValue("involved-party"))
			{
				involvedParty = get(IInvolvedPartyService.class).findByID(UUID.fromString(session.as("involved-party", String.class)));
				session.setInvolvedParty(involvedParty);
			}
			else
			{
				LogFactory.getLog("SessionMasterService")
				          .warning("Session has no involved party. First session call?");
				return session;
			}
		}
		try
		{
			AsyncSessionUpdate update = get(AsyncSessionUpdate.class);
			update.setSession(session)
			      .setSessionSystem(sessionSystem)
			      .setIdentityToken(identityToken);
			update.run();
		/*	JobService.getInstance()
			          .addJob("AsyncSessionUpdate", update);*/
		}
		catch (Throwable e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
		}
		return session;
	}
	
	
}
