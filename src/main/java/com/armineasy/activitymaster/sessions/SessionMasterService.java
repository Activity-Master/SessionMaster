package com.armineasy.activitymaster.sessions;

import com.armineasy.activitymaster.activitymaster.services.classifications.enterprise.IEnterpriseName;
import com.armineasy.activitymaster.activitymaster.services.dto.*;
import com.armineasy.activitymaster.activitymaster.services.security.Passwords;
import com.armineasy.activitymaster.activitymaster.services.system.IEnterpriseService;
import com.armineasy.activitymaster.sessions.services.ISession;
import com.armineasy.activitymaster.sessions.services.ISessionMasterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Singleton;
import com.guicedee.logger.LogFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import static com.armineasy.activitymaster.activitymaster.services.classifications.resourceitems.ResourceItemTypes.*;
import static com.armineasy.activitymaster.sessions.services.classifications.SessionClassifications.*;
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
	public ISession<?> getSession(IInvolvedParty<?> involvedParty, IEnterpriseName<?> enterpriseName, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(enterpriseName);
		ISystems<?> sessionSystem = SessionMasterSystem.getNewSystem()
		                                               .get(enterprise);
		ISession<?> session = get(ISession.class);
		try
		{
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
			IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = involvedParty.addOrReuse(SessionObject,
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
	public ISession<?> updateSession(ISession<?> session, UUID... identityToken)
	{
		IEnterprise<?> enterprise = get(IEnterpriseService.class).getEnterprise(session.getEnterpriseName());
		ISystems<?> sessionSystem = SessionMasterSystem.getNewSystem()
		                                               .get(enterprise);
		try
		{
			String sessionString = get(DefaultObjectMapper).writeValueAsString(session);
			sessionString = new Passwords().integerEncrypt(sessionString.getBytes());
			IRelationshipValue<IInvolvedParty<?>, IResourceItem<?>, ?> sessionObject = session.getInvolvedParty()
			                                                                                  .addOrUpdate(SessionObject,
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
