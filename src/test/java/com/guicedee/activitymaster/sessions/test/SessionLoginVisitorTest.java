package com.guicedee.activitymaster.sessions.test;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.guicedee.activitymaster.fsdm.client.services.IEnterpriseService;
import com.guicedee.activitymaster.fsdm.client.services.SessionUtils;
import com.guicedee.activitymaster.fsdm.client.services.administration.ActivityMasterConfiguration;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISessionLoginService;
import com.guicedee.activitymaster.sessions.services.IUserSessionService;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.utils.LogUtils;
import io.smallrye.mutiny.Uni;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the user-sessions module's core purpose:
 * <em>persisted session data identified by the unique JWebMP web-client key, with the backing
 * guest/visitor involved party provisioned on demand.</em>
 *
 * <p>Exercises {@link ISessionLoginService#loginVisitor} on both a {@link Mutiny.Session} and a
 * {@link Mutiny.StatelessSession}: a fresh web-client UUID must create a device (guest/visitor)
 * involved party and persist its session, and re-presenting the same key must resolve the
 * <em>same</em> involved party (the identity guarantee).</p>
 */
@Log4j2
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SessionLoginVisitorTest
{
	private static final String ENTERPRISE = "SessionTestCo";
	private static final String SESSION_SYSTEM = IUserSessionService.SessionMasterSystemName;

	private Mutiny.SessionFactory sessionFactory;

	@BeforeAll
	public void setup()
	{
		LogUtils.addConsoleLogger(Level.INFO);
		ActivityMasterConfiguration.get().setApplicationEnterpriseName(ENTERPRISE);
		IGuiceContext.instance();

		sessionFactory = IGuiceContext.get(Key.get(Mutiny.SessionFactory.class, Names.named("ActivityMaster-Test")));
		assertNotNull(sessionFactory, "SessionFactory should not be null");

		IEnterpriseService<?> es = IGuiceContext.get(IEnterpriseService.class);

		// Provision the enterprise (registers every system, incl. the Involved Party system that creates
		// the Device involved-party type, and the Profile/Session systems).
		sessionFactory.withSession(session -> session.withTransaction(tx ->
				es.getEnterprise(session, ENTERPRISE)
						.onFailure().recoverWithUni(t -> {
							var ent = es.get();
							ent.setName(ENTERPRISE);
							ent.setDescription("User-session test enterprise");
							return es.createNewEnterprise(session, ent)
									.chain(e -> es.startNewEnterprise(session, ENTERPRISE, "admin", "adminadmin!@"));
						})
						.replaceWith(Uni.createFrom().voidItem())
		)).await().atMost(Duration.ofMinutes(3));

		IEnterprise<?, ?> enterprise = sessionFactory.withSession(s -> es.getEnterprise(s, ENTERPRISE))
				.await().atMost(Duration.ofMinutes(1));
		assertNotNull(enterprise, "Baseline enterprise must be provisioned in setup");

		// Run all ISystemUpdate installs (Profiles → web-client identification type; Sessions → session
		// classifications + event types) so the visitor/session flow has its taxonomy.
		Integer updates = sessionFactory.withSession(session -> session.withTransaction(tx ->
				es.loadUpdates(session, enterprise)
		)).await().atMost(Duration.ofMinutes(5));
		log.info("Applied {} system updates for {}", updates, ENTERPRISE);
	}

	@Test
	@Order(1)
	@DisplayName("loginVisitor creates a guest/visitor involved party for a new key and is idempotent (stateful)")
	public void loginVisitorStateful_createsAndResolvesByKey()
	{
		UUID webClientKey = UUID.randomUUID();

		// First visit — creates the device/guest involved party + persisted session
		UUID firstIdentity = SessionUtils.<UUID>withActivityMaster(ENTERPRISE, SESSION_SYSTEM, tuple -> {
			Mutiny.Session session = tuple.getItem1();
			ISessionLoginService<?> loginService = IGuiceContext.get(ISessionLoginService.class);
			ProfileServiceDTO<?> dto = IGuiceContext.get(ProfileServiceDTO.class);
			dto.setWebClientUUID(webClientKey);
			dto.setEnterprise(tuple.getItem2());
			return loginService.loginVisitor(session, dto, tuple.getItem3(), tuple.getItem4())
					.map(ProfileServiceDTO::getIdentityToken);
		}).await().atMost(Duration.ofMinutes(2));

		assertNotNull(firstIdentity, "A guest/visitor involved party must be created for a new web-client key");

		// Second visit with the SAME key — must resolve the SAME involved party
		UUID secondIdentity = SessionUtils.<UUID>withActivityMaster(ENTERPRISE, SESSION_SYSTEM, tuple -> {
			Mutiny.Session session = tuple.getItem1();
			ISessionLoginService<?> loginService = IGuiceContext.get(ISessionLoginService.class);
			ProfileServiceDTO<?> dto = IGuiceContext.get(ProfileServiceDTO.class);
			dto.setWebClientUUID(webClientKey);
			dto.setEnterprise(tuple.getItem2());
			return loginService.loginVisitor(session, dto, tuple.getItem3(), tuple.getItem4())
					.map(ProfileServiceDTO::getIdentityToken);
		}).await().atMost(Duration.ofMinutes(2));

		assertEquals(firstIdentity, secondIdentity,
				"The same web-client key must resolve the same persisted involved party");
	}

	@Test
	@Order(2)
	@DisplayName("loginVisitor creates a guest/visitor involved party for a new key and is idempotent (stateless)")
	public void loginVisitorStateless_createsAndResolvesByKey()
	{
		UUID webClientKey = UUID.randomUUID();

		UUID firstIdentity = SessionUtils.<UUID>withActivityMasterStateless(ENTERPRISE, SESSION_SYSTEM, tuple -> {
			Mutiny.StatelessSession session = tuple.getItem1();
			ISessionLoginService<?> loginService = IGuiceContext.get(ISessionLoginService.class);
			ProfileServiceDTO<?> dto = IGuiceContext.get(ProfileServiceDTO.class);
			dto.setWebClientUUID(webClientKey);
			dto.setEnterprise(tuple.getItem2());
			return loginService.loginVisitor(session, dto, tuple.getItem3(), tuple.getItem4())
					.map(ProfileServiceDTO::getIdentityToken);
		}).await().atMost(Duration.ofMinutes(2));

		assertNotNull(firstIdentity, "A guest/visitor involved party must be created statelessly for a new key");

		UUID secondIdentity = SessionUtils.<UUID>withActivityMasterStateless(ENTERPRISE, SESSION_SYSTEM, tuple -> {
			Mutiny.StatelessSession session = tuple.getItem1();
			ISessionLoginService<?> loginService = IGuiceContext.get(ISessionLoginService.class);
			ProfileServiceDTO<?> dto = IGuiceContext.get(ProfileServiceDTO.class);
			dto.setWebClientUUID(webClientKey);
			dto.setEnterprise(tuple.getItem2());
			return loginService.loginVisitor(session, dto, tuple.getItem3(), tuple.getItem4())
					.map(ProfileServiceDTO::getIdentityToken);
		}).await().atMost(Duration.ofMinutes(2));

		assertEquals(firstIdentity, secondIdentity,
				"The same web-client key must resolve the same persisted involved party (stateless)");
	}
}

