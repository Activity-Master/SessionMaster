package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.activitymaster.client.services.IRelationshipValue;
import com.guicedee.activitymaster.client.services.IResourceItemService;
import com.guicedee.activitymaster.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.logger.LogFactory;
import jakarta.cache.annotation.CacheKey;
import jakarta.cache.annotation.CacheResult;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static com.guicedee.activitymaster.client.services.classifications.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;
import static com.guicedee.guicedinjection.json.StaticStrings.*;

public class SessionMasterService
		implements ISessionMasterService<SessionMasterService>
{
	private final ObjectMapper mapper = GuiceContext.get(ObjectMapper.class);
	private final TypeFactory typeFactory = mapper.getTypeFactory();
	private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);
	
	@Inject
	private IEnterprise<?,?> enterprise;
	@Inject
	@Named(SessionMasterSystemName)
	private ISystems<?,?> sessionSystem;
	@Inject
	@Named(SessionMasterSystemName)
	private UUID identityToken;
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?,?> involvedParty,@CacheKey ISystems<?,?> system, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), system, identityToken);
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> original,@CacheKey  ISystems<?,?> system, UUID... identityToken)
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
			
			IResourceItemService<?> resourceItemService = get(IResourceItemService.class);
			Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem = involvedParty.findResourceItem(SessionClassifications.SessionObject.toString(),null, system,false,false, identityToken);
			if(resourceItem.isEmpty())
			{
				var newResource =
						resourceItemService.create(JsonPacket.toString(), sessionString, system, identityToken);
				resourceItem = Optional.of(involvedParty.addResourceItem(SessionClassifications.SessionObject.toString(), newResource, STRING_EMPTY, system, identityToken));
			}
			String currentSessionValue = new String(resourceItem.get().getSecondary().getData());
			if (!Strings.isNullOrEmpty(currentSessionValue))
			{
				sessionString = currentSessionValue;
			}
			else
			{
				currentSessionValue = "{}";
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
	public ISession<?> expireSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> original,@CacheKey ISystems<?,?> system, UUID... identityToken)
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
			Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem = involvedParty.findResourceItem(SessionClassifications.SessionObject.toString(),null, system,false,false, identityToken);
			resourceItem.get()
			            .expire(identityToken);
			resourceItem.get().getSecondary().expire();
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
	public ISession<?> updateSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> session,@CacheKey ISystems<?,?> system, UUID... identityToken)
	{
		try
		{
			if (session == null && involvedParty == null)
			{
				LogFactory.getLog("SessionMasterService")
				          .finer("Session has no involved party. First session call?");
				return session;
			}
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);

			var resourceItem =
					involvedParty.findResourceItem(SessionClassifications.SessionObject.toString(),null, system,false,false, identityToken);
			resourceItem.get()
			            .getSecondary()
			            .updateData(sessionString.getBytes(), system, identityToken);
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
	
	
}
