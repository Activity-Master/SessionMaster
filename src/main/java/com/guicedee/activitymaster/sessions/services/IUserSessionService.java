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
    /** Read one value under the account's transaction lock. May create the canonical SessionObject on first access. */
    Uni<String> readSessionValue(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session,
            IInvolvedParty<?, ?> party, ISystems<?, ?> system, String key, java.util.UUID... identityToken);

    /** Compare the previous string and atomically replace ONLY this value; never serialize a stale complete map. */
    Uni<Void> compareAndSetSessionValue(org.hibernate.reactive.mutiny.Mutiny.StatelessSession session,
            IInvolvedParty<?, ?> party, ISystems<?, ?> system, String key, String expected, String replacement,
            java.util.UUID... identityToken);

    final class ValueConflict extends RuntimeException {
        public ValueConflict() { super("Session preference changed; reload before saving"); }
    }
}
