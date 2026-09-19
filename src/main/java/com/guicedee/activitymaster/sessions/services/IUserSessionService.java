package com.guicedee.activitymaster.sessions.services;

import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import io.smallrye.mutiny.Uni;

public interface IUserSessionService<J extends IUserSessionService<J>>
{
	String SessionMasterSystemName = "Sessions Master";

	Uni<IUserSession<?>> getUserSession(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session, IInvolvedParty<?, ?> involvedParty, ISystems<?, ?> system, java.util.UUID... identityToken);

	Uni<IUserSession<?>> getUserSession(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);

	Uni<IUserSession<?>> expireSession(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> original, ISystems<?, ?> system, java.util.UUID... identityToken);

	Uni<IUserSession<?>> updateSession(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session, IInvolvedParty<?, ?> involvedParty, IUserSession<?> userSession, ISystems<?, ?> system, java.util.UUID... identityToken);
}
