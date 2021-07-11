package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.guicedee.activitymaster.fsdm.client.services.IRelationshipValue;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
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

import static com.guicedee.activitymaster.fsdm.client.services.classifications.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;
import static com.guicedee.guicedinjection.json.StaticStrings.*;

public class SessionMasterService
		implements ISessionMasterService<SessionMasterService>
{
	private final ObjectMapper mapper = GuiceContext.get(ObjectMapper.class);
	private final TypeFactory typeFactory = mapper.getTypeFactory();
	private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);

	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISystems<?,?> system, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), system, identityToken);
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> original,ISystems<?,?> system, UUID... identityToken)
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
				resourceItem = saveNewSessionResourceItem(involvedParty, system, sessionString, resourceItemService, identityToken);
			}
			String currentSessionValue = new String(resourceItem.get().getSecondary().getData());
			
			if (!Strings.isNullOrEmpty(currentSessionValue))
			{
				sessionString = currentSessionValue;
			}
			else
			{
				sessionString = "{}";
			}

			HashMap<String, String> returned = new HashMap<>();
			if (!(sessionString.isEmpty() || "{}".equals(sessionString)))
			{
				try
				{
					returned = get(DefaultObjectMapper).readValue(sessionString, mapType);
					Session sess = (Session) session;
					sess.getValues()
					    .putAll(returned);
				}
				catch (JsonParseException ioException)
				{
					//Guest?
					LogFactory.getLog("SessionMasterService")
					          .log(Level.FINE, "Error serializing the incoming object to retrieve a session", ioException);
				}
			}
		}
		catch (Exception e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
		}
		if (session != null)
		{
			session.setSystem(system);
			session.setInvolvedParty(involvedParty);
		}
		return session;
	}
	
	//@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> saveNewSessionResourceItem(IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, String sessionString, IResourceItemService<?> resourceItemService, UUID[] identityToken)
	{
		Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem;
		var newResource =
				resourceItemService.create(JsonPacket.toString(), "application/json", system, identityToken);
		newResource.updateData(sessionString.getBytes(), system, identityToken);
		resourceItem = Optional.of(involvedParty.addResourceItem(SessionClassifications.SessionObject.toString(), newResource, STRING_EMPTY, system, identityToken));
		return resourceItem;
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache",skipGet = true)
	public ISession<?> updateCache(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> original,ISystems<?,?> system, UUID... identityToken)
	{
		return original;
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache",skipGet = true)
	public ISession<?> expireSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> original,ISystems<?,?> system, UUID... identityToken)
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
	public ISession<?> updateSession(@CacheKey IInvolvedParty<?,?> involvedParty, ISession<?> session,ISystems<?,?> system, UUID... identityToken)
	{
		try
		{
			if (system.isFake() || (session == null && involvedParty == null))
			{
				LogFactory.getLog("SessionMasterService")
				          .finer("Session has no involved party. First session call?");
				return session;
			}
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			if (Strings.isNullOrEmpty(sessionString))
			{
				sessionString = "{}";
			}

			var resourceItem =
					involvedParty.findResourceItem(SessionClassifications.SessionObject.toString(),null, system,false,false, identityToken);
			if(resourceItem.isPresent())
			{
				resourceItem.get()
				            .getSecondary()
				            .updateData(sessionString.getBytes(), system, identityToken);
			}
			else
			{
				IResourceItemService<?> resourceItemService = GuiceContext.get(IResourceItemService.class);
				IResourceItem<?, ?> iResourceItem = resourceItemService.create(JsonPacket.toString(), "application/json", system, identityToken);
				iResourceItem.updateData(sessionString.getBytes(),system,identityToken);
			}
		}
		catch (IOException e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
		}
		if (session != null)
		{
			session.setSystem(system);
			session.setInvolvedParty(involvedParty);
		}
		return session;
	}
	
	
}
