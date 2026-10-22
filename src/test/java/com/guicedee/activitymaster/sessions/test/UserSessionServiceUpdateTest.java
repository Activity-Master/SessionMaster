package com.guicedee.activitymaster.sessions.test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.guicedee.activitymaster.fsdm.client.services.IResourceItemService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.party.IInvolvedParty;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.sessions.UserSession;
import com.guicedee.activitymaster.sessions.UserSessionService;
import com.guicedee.activitymaster.sessions.services.IUserSession;
import com.guicedee.client.IGuiceContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.guicedee.client.implementations.ObjectBinderKeys.DefaultObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated service regressions. The resource writer owns persistence; no database or identity is fabricated. */
class UserSessionServiceUpdateTest {
    @Test
    void returnsTheOriginalUserSessionOnlyAfterTheResourceWriteCompletes() throws Exception {
        try (var f = new Fixture()) {
            var write = new AtomicReference<UniEmitter<? super Void>>();
            when(f.items.updateResourceData(eq(f.db), any(byte[].class), eq(f.resource)))
                    .thenReturn(Uni.createFrom().<Void>emitter(write::set));
            var result = new AtomicReference<IUserSession<?>>();
            var failure = new AtomicReference<Throwable>();
            f.service.updateSession(f.db, f.party, f.preferences, f.system, f.actor)
                    .subscribe().with(result::set, failure::set);
            assertNotNull(write.get(), "the real resource write was subscribed");
            assertNull(result.get(), "a pending write cannot report success");
            assertNull(failure.get());
            write.get().complete(null);
            assertSame(f.preferences, result.get(), "return the IUserSession, never the database session");
            assertNull(failure.get());
            var bytes = ArgumentCaptor.forClass(byte[].class);
            verify(f.items).updateResourceData(eq(f.db), bytes.capture(), eq(f.resource));
            var payload = f.mapper.readTree(bytes.getValue());
            assertEquals("fr", payload.get("unrelated.language").asString());
            assertEquals("sage", payload.get("chess.board").asString());
            assertEquals(2, payload.size());
            assertEquals(f.resource, result.get().getResourceItemID());
            assertEquals(f.data, result.get().getDataID());
        }
    }

    @Test
    void resourceWriteFailurePropagatesWithoutReturningASession() {
        try (var f = new Fixture()) {
            var write = new AtomicReference<UniEmitter<? super Void>>();
            when(f.items.updateResourceData(eq(f.db), any(byte[].class), eq(f.resource)))
                    .thenReturn(Uni.createFrom().<Void>emitter(write::set));
            var result = new AtomicReference<IUserSession<?>>();
            var failure = new AtomicReference<Throwable>();
            f.service.updateSession(f.db, f.party, f.preferences, f.system, f.actor)
                    .subscribe().with(result::set, failure::set);
            var denied = new IllegalStateException("fixture write failed");
            write.get().fail(denied);
            assertSame(denied, failure.get());
            assertNull(result.get(), "failed persistence cannot report a saved session");
            assertEquals("fr", f.preferences.getValues().get("unrelated.language"));
        }
    }

    @Test
    void fakeSystemReturnsTheUserSessionWithoutClaimingAResourceWrite() {
        try (var f = new Fixture()) {
            when(f.system.isFake()).thenReturn(true);
            assertSame(f.preferences, f.service.updateSession(f.db, f.party, f.preferences, f.system, f.actor)
                    .await().atMost(Duration.ofSeconds(5)));
            verifyNoInteractions(f.items);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void atomicReadDeniesAnOwnerWithoutWritePermissionBeforeLoadingSessionData() {
        try (var f = new Fixture()) {
            var enterprise = mock(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise.class);
            when(enterprise.getId()).thenReturn(UUID.randomUUID());when(f.system.getEnterprise()).thenReturn(enterprise);
            when(f.party.getId()).thenReturn(UUID.randomUUID());
            when(f.db.createNativeQuery(anyString(),eq(Integer.class)).setParameter(anyString(),any()).getSingleResult()).thenReturn(Uni.createFrom().item(1));
            when(((com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable)f.party)
                    .canWrite(eq(f.db),eq(f.system),any(UUID[].class))).thenReturn(Uni.createFrom().item(false));
            assertThrows(SecurityException.class,()->f.service.readSessionValue(f.db,f.party,f.system,"ne1.plugin.chess-styles.social",f.actor)
                    .await().atMost(Duration.ofSeconds(5)));
            verifyNoInteractions(f.items);
        }
    }
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void atomicWriteDeniesAReadOnlyCanonicalResourceBeforeMutation() {
        try (var f = new Fixture()) {
            var enterprise = mock(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise.class);
            when(enterprise.getId()).thenReturn(UUID.randomUUID());when(f.system.getEnterprise()).thenReturn(enterprise);
            when(f.party.getId()).thenReturn(UUID.randomUUID());
            when(f.db.createNativeQuery(anyString(),eq(Integer.class)).setParameter(anyString(),any()).getSingleResult()).thenReturn(Uni.createFrom().item(1));
            when(((com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable)f.party)
                    .canWrite(eq(f.db),eq(f.system),any(UUID[].class))).thenReturn(Uni.createFrom().item(true));
            var canonical = spy(f.service);
            doReturn(Uni.createFrom().item(f.preferences)).when(canonical).getUserSession(eq(f.db),eq(f.party),eq(f.system),any(UUID[].class));
            var resource = mock(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem.class,
                    withSettings().extraInterfaces(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable.class));
            when(f.items.findByUUID(f.db,f.resource)).thenReturn(Uni.createFrom().item(resource));
            when(((com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable)resource)
                    .canWrite(eq(f.db),eq(f.system),any(UUID[].class))).thenReturn(Uni.createFrom().item(false));
            assertThrows(SecurityException.class,()->canonical.compareAndSetSessionValue(f.db,f.party,f.system,"ne1.plugin.chess-styles.social",null,"value",f.actor)
                    .await().atMost(Duration.ofSeconds(5)));
            verify(f.db,never()).createQuery(anyString(),eq(UUID.class));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void atomicWriteResolvesOnlyTheCanonicalResourceAndRejectsALostCompareAndSet() {
        try (var f = new Fixture()) {
            var enterprise = mock(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise.class);
            when(enterprise.getId()).thenReturn(UUID.randomUUID());when(f.system.getEnterprise()).thenReturn(enterprise);when(f.party.getId()).thenReturn(UUID.randomUUID());
            when(f.db.createNativeQuery(anyString(),eq(Integer.class)).setParameter(anyString(),any()).getSingleResult()).thenReturn(Uni.createFrom().item(1));
            when(((com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable)f.party)
                    .canWrite(eq(f.db),eq(f.system),any(UUID[].class))).thenReturn(Uni.createFrom().item(true));
            var canonical=spy(f.service);doReturn(Uni.createFrom().item(f.preferences)).when(canonical).getUserSession(eq(f.db),eq(f.party),eq(f.system),any(UUID[].class));
            var resource=mock(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.resourceitem.IResourceItem.class,
                    withSettings().extraInterfaces(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable.class));
            when(f.items.findByUUID(f.db,f.resource)).thenReturn(Uni.createFrom().item(resource));
            when(((com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable)resource)
                    .canWrite(eq(f.db),eq(f.system),any(UUID[].class))).thenReturn(Uni.createFrom().item(true));
            when(f.db.createQuery(anyString(),eq(UUID.class)).setParameter("resource",f.resource).getSingleResultOrNull()).thenReturn(Uni.createFrom().item(f.data));
            var update=mock(Mutiny.Query.class,RETURNS_SELF);when(f.db.createNativeQuery(anyString())).thenReturn(update);
            when(update.executeUpdate()).thenReturn(Uni.createFrom().item(1));
            canonical.compareAndSetSessionValue(f.db,f.party,f.system,"ne1.plugin.chess-styles.social","old","new",f.actor).await().atMost(Duration.ofSeconds(5));
            verify(update).setParameter("dataValueId",f.data);verify(update).setParameter("preferenceKey","ne1.plugin.chess-styles.social");
            verify(update).setParameter("expected","old");verify(update).setParameter("replacement","new");
            when(update.executeUpdate()).thenReturn(Uni.createFrom().item(0));
            assertThrows(com.guicedee.activitymaster.sessions.services.IUserSessionService.ValueConflict.class,()->canonical
                    .compareAndSetSessionValue(f.db,f.party,f.system,"ne1.plugin.chess-styles.social","old","new",f.actor).await().atMost(Duration.ofSeconds(5)));
            verify(f.items,never()).updateResourceData(any(),any(byte[].class),any(UUID.class));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Fixture implements AutoCloseable {
        final ObjectMapper mapper = new ObjectMapper();
        final IResourceItemService items = mock(IResourceItemService.class);
        final Mutiny.StatelessSession db = mock(Mutiny.StatelessSession.class, RETURNS_DEEP_STUBS);
        final IInvolvedParty party = mock(IInvolvedParty.class, withSettings().extraInterfaces(com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.base.IWarehouseCoreTable.class));
        final ISystems system = mock(ISystems.class);
        final UUID resource = UUID.randomUUID(), data = UUID.randomUUID(), actor = UUID.randomUUID();
        final UserSession preferences = new UserSession();
        final MockedStatic<IGuiceContext> context = mockStatic(IGuiceContext.class);
        final UserSessionService service;
        Fixture() {
            preferences.setResourceItemID(resource);
            preferences.setDataID(data);
            preferences.addValue("unrelated.language", "fr");
            preferences.addValue("chess.board", "sage");
            context.when(() -> IGuiceContext.get(ObjectMapper.class)).thenReturn(mapper);
            context.when(() -> IGuiceContext.get(DefaultObjectMapper)).thenReturn(mapper);
            service = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(new TypeLiteral<IResourceItemService<?>>() {}).toInstance(items);
                    bind(Vertx.class).toInstance(mock(Vertx.class));
                }
            }).getInstance(UserSessionService.class);
        }
        @Override public void close() { context.close(); }
    }
}
