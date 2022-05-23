package com.guicedee.activitymaster.sessions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.guicedee.activitymaster.fsdm.client.services.IRelationshipValue;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceData;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.exceptions.ResourceItemException;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedinjection.GuiceContext;
import com.guicedee.logger.LogFactory;
import jakarta.cache.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static com.guicedee.activitymaster.fsdm.client.services.classifications.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;
import static com.guicedee.guicedinjection.json.StaticStrings.*;

public class UserSessionService
		implements IUserSessionService<UserSessionService>
{
	private final ObjectMapper mapper = GuiceContext.get(ObjectMapper.class);
	private final TypeFactory typeFactory = mapper.getTypeFactory();
	private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);
	
	@Inject
	private IResourceItemService<?> resourceItemService;
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public IUserSession<?> getSession(@CacheKey IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		return getSession(involvedParty, new UserSession(), system, identityToken);
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache")
	public IUserSession<?> getSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
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
			
			Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem = involvedParty.
					findResourceItem(SessionClassifications.SessionObject.toString(), null, system, false, false, identityToken);
			if (resourceItem.isEmpty())
			{
				resourceItem = saveNewSessionResourceItem(involvedParty, system, sessionString, resourceItemService, identityToken);
			}
			IResourceItem<?, ?> secondary = resourceItem.get()
			                                            .getSecondary();
			Optional<IResourceData<?, ?>> data = secondary.getDataRow(identityToken);
			String currentSessionValue = "";
			if (data.isPresent())
			{
				currentSessionValue = new String(data.get()
				                                     .getResourceItemData());
			}
			else
			{
				var r =
						saveNewSessionResourceItem(involvedParty, system, "{}", resourceItemService, identityToken);
				data = r.get().getSecondary().getDataRow();
				//System.out.println("Creating a new user session object.....");
			}
			
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
					session.getValues()
					       .putAll(returned);
				}
				catch (JsonParseException ioException)
				{
					//Guest?
					LogFactory.getLog("SessionMasterService")
					          .log(Level.FINE, "Error serializing the incoming object to retrieve a session", ioException);
				}
			}
			
			session.setResourceItemID(UUID.fromString(secondary.getId()));
			session.setDataID(UUID.fromString(data.get()
			                                      .getId()));
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
	Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> saveNewSessionResourceItem(IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, String sessionString, IResourceItemService<?> resourceItemService, java.util.UUID[] identityToken)
	{
		Optional<? extends IRelationshipValue<?, IResourceItem<?, ?>, ?>> resourceItem;
		
		var newResource =
				resourceItemService.create(JsonPacket.toString(), "application/json", system, identityToken);
		
		newResource.updateData(sessionString.getBytes(), system, identityToken);
		resourceItem = Optional.of(involvedParty.addResourceItem(SessionClassifications.SessionObject.toString(), newResource, STRING_EMPTY, system, identityToken));
		
		return resourceItem;
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache", skipGet = true)
	public IUserSession<?> updateCache(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		return original;
	}
	
	@Override
	@CacheRemove(cacheName = "SessionsCache")
	public void removeCache(@CacheKey IInvolvedParty<?, ?> involvedParty)
	{
	}
	
	@Override
	@CacheResult(cacheName = "SessionsCache", skipGet = true)
	//@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	public IUserSession<?> expireSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken)
	{
		IUserSession<?> session = original;
		try
		{
			if (session == null && involvedParty == null)
			{
				LogFactory.getLog("SessionMasterService")
				          .finer("Session has no involved party. First session call?");
				return session;
			}
			IResourceItemService<?> resourceItemService = GuiceContext.get(IResourceItemService.class);
			var resourceItem
					= resourceItemService.findByUUID(original.getResourceItemID());
			resourceItem.expire();
			resourceItem.getDataRow();
		}
		catch (Exception e)
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
	@CacheResult(cacheName = "SessionsCache", skipGet = true)
	//@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	public IUserSession<?> updateSession(@CacheKey IInvolvedParty<?, ?> involvedParty, IUserSession<?> session, ISystems<?, ?> system, java.util.UUID... identityToken)
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
			IResourceItemService<?> resourceItemService = GuiceContext.get(IResourceItemService.class);
			var resourceItem =
					resourceItemService.findByUUID(session.getResourceItemID());
			if (resourceItem != null)
			{
				var dataRow = resourceItem.getDataRow();
				if (dataRow.isPresent())
				{
					resourceItem.updateData(sessionString.getBytes(), system, identityToken);
				}
				else
				{
					throw new ResourceItemException("Cannot update a session that has no active data row attached");
				}
			}
			else
			{
				if (session.getResourceItemID() == null && session.getDataID() == null && session.getInvolvedParty() != null)
				{
					return getSession(involvedParty, session, system, identityToken);
				}
				throw new ResourceItemException("Cannot update a session that has no resource item UUID attached");
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
