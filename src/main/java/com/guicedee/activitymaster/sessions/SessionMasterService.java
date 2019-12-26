package com.guicedee.activitymaster.sessions;

import com.guicedee.activitymaster.core.services.classifications.enterprise.IEnterpriseName;
import com.guicedee.activitymaster.core.services.dto.*;
import com.guicedee.activitymaster.core.services.security.Passwords;
import com.guicedee.activitymaster.core.services.system.IEnterpriseService;
import com.guicedee.activitymaster.sessions.services.ISession;
import com.guicedee.activitymaster.sessions.services.ISessionMasterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Singleton;
import com.guicedee.activitymaster.sessions.services.classifications.SessionClassifications;
import com.guicedee.logger.LogFactory;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import static com.guicedee.activitymaster.core.services.classifications.resourceitems.ResourceItemTypes.*;
import static com.guicedee.guicedinjection.GuiceContext.*;
import static com.guicedee.guicedinjection.interfaces.ObjectBinderKeys.*;

@Singleton
public class SessionMasterService
		implements ISessionMasterService<SessionMasterService>
{
	private final ObjectMapper mapper = new ObjectMapper();
	private final TypeFactory typeFactory = mapper.getTypeFactory();
	private final MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, String.class);

	@Override
	@CacheResult(cacheName = "SessionCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty, IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		return getSession(involvedParty, new Session(), enterpriseName, identityToken);
	}

	@Override
	@CacheResult(cacheName = "SessionCache")
	public ISession<?> getSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> original, IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(enterpriseName);
		ISystems<?> sessionSystem = SessionMasterSystem.getSystemsMap()
		                                               .get(enterprise);
		ISession<?> session = original;
		try
		{
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
			IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = involvedParty.addOrReuse(SessionClassifications.SessionObject,
			                                                                                                    Documents,
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
		session.setInvolvedParty(involvedParty);
		session.setEnterpriseName(enterpriseName);
		return session;
	}

	@Override
	@CacheResult(cacheName = "SessionCache",
			skipGet = true)
	public ISession<?> updateSession(@CacheKey IInvolvedParty<?> involvedParty, ISession<?> session, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(session.getEnterpriseName());
		ISystems<?> sessionSystem = SessionMasterSystem.getSystemsMap()
		                                               .get(enterprise);
		try
		{
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
			IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = session.getInvolvedParty()
			                                                                                  .addOrUpdate(SessionClassifications.SessionObject,
			                                                                                               Documents,
			                                                                                               null,
			                                                                                               null,
			                                                                                               sessionString.getBytes(), "encrypted/json", sessionSystem,
			                                                                                               identityToken);
			sessionObject.getSecondary()
			             .updateData(sessionString.getBytes(), identityToken);
		}
		catch (IOException e)
		{
			LogFactory.getLog("SessionMasterService")
			          .log(Level.SEVERE, "Error serializing the incoming object to retrieve a session", e);
		}
		return session;
	}
}
