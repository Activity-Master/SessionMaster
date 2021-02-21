package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.core.services.security.Passwords;
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
	
	@Inject
	private IEnterprise<?> enterprise;
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?> sessionSystem;
	@Inject
	@Named(SessionMasterSystemName)
	private UUID identityToken;
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty,@CacheKey ISystems<?> system, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), system, identityToken);
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> original,@CacheKey  ISystems<?> system, UUID... identityToken)
	{
		ISession<?> session = original;
		try
		{
			if (session == null && involvedParty == null)
			{
				LogFactory.getLog("SessionMasterService")
				          .finer("Session has no involved party. First session call?");
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
			byte[] storedSession = null;
			try
			{
				IResourceItem<?> secondary = sessionObject.getSecondary();
				secondary.getData(identityToken);
				storedSession = secondary
						.getData();
				sessionString = new String(new Passwords().integerDecrypt(new String(storedSession)));
			}
			catch (Exception e)
			{
				sessionString = "{}";
			}
			
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
			session.setSystem(system);
			session.setInvolvedParty(involvedParty);
		}
		return session;
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache",skipGet = true)
	public ISession<?> expireSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> original,@CacheKey ISystems<?> system, UUID... identityToken)
	{
		ISession<?> session = original;
		try
		{
			if (session == null && involvedParty == null)
			{
				LogFactory.getLog("SessionMasterService")
				          .finer("Session has no involved party. First session call?");
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
			
			sessionObject.getSecondary()
			             .remove();
			sessionObject.expire();
		}
		catch (IOException e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the inecoming object to retrieve a session", e);
		}
		if (session != null)
		{
			session.setSystem(system);
			session.setInvolvedParty(involvedParty);
		}
		return session;
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache",skipGet = true)
	public ISession<?> createSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> session,@CacheKey ISystems<?> system, UUID... identityToken)
	{
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
	
	@Override
	@CacheResult(cacheName = "SessionsCache",skipGet = true)
	public ISession<?> updateSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> session,@CacheKey ISystems<?> system, UUID... identityToken)
	{
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
