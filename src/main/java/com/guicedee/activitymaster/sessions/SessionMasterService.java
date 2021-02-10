package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.core.services.security.Passwords;
import com.guicedee.activitymaster.core.services.system.IInvolvedPartyService;
import com.guicedee.activitymaster.sessions.implementations.AsyncSessionUpdate;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.logger.LogFactory;
import jakarta.cache.annotation.CacheKey;
import jakarta.cache.annotation.CacheRemove;
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
	public ISession<?> getSession(IInvolvedParty<?> involvedParty, ISystems<?> system, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), system, identityToken);
	}
	
	@Override
	public ISession<?> getSession(IInvolvedParty<?> involvedParty, ISession<?> original, ISystems<?> system, UUID... identityToken)
	{
		IEnterprise<?> enterprise = system.getEnterprise();
		ISystems<?> sessionSystem = get(SessionMasterSystem.class).getSystem(enterprise);
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
	public ISession<?> expireSession(IInvolvedParty<?> involvedParty, ISession<?> original, ISystems<?> system, UUID... identityToken)
	{
		IEnterprise<?> enterprise = system.getEnterprise();
		ISystems<?> sessionSystem = get(SessionMasterSystem.class).getSystem(enterprise);
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
	public ISession<?> updateSession(IInvolvedParty<?> involvedParty, ISession<?> session, ISystems<?> system, UUID... identityToken)
	{
		IEnterprise<?> enterprise = system.getEnterprise();
		ISystems<?> sessionSystem = get(SessionMasterSystem.class).getSystem(enterprise);
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
