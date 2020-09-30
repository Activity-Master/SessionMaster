package com.guicedee.activitymaster.sessions.implementations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.guicedee.activitymaster.core.db.ActivityMasterDB;
import com.guicedee.activitymaster.core.services.dto.IInvolvedParty;
import com.guicedee.activitymaster.core.services.dto.IRelationshipValue;
import com.guicedee.activitymaster.core.services.dto.IResourceItem;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.services.security.Passwords;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.guicedpersistence.db.annotations.Transactional;
import com.guicedee.logger.LogFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.core.services.classifications.resourceitems.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@Getter
@Setter
@Accessors(chain = true)
public class AsyncSessionUpdate implements Runnable
{
	private ISession<?> session;
	private ISystems<?> sessionSystem;
	private UUID[] identityToken;
	
	@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	private void sessionUpdate() throws JsonProcessingException
	{
		String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
		sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
		IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = session.getInvolvedParty()
		                                                                                  .addOrUpdateResourceItem(SessionClassifications.SessionObject,
		                                                                                                           JsonPacket,
		                                                                                                           null,
		                                                                                                           null,
		                                                                                                           sessionString.getBytes(), "encrypted/json", sessionSystem,
		                                                                                                           identityToken);
		sessionObject.getSecondary()
		             .updateData(sessionString.getBytes(), identityToken);
	}
	
	@Override
	public void run()
	{
		try
		{
			sessionUpdate();
		}
		catch (Throwable t)
		{
			LogFactory.getLog(getClass())
			          .log(Level.SEVERE, "Unable to update session! - ", t);
		}
	}
}
