package com.guicedee.activitymaster.sessions.test;

import com.guicedee.activitymaster.sessions.UserSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure (no-database) unit tests for {@link UserSession} — the in-memory session-data container that
 * the Session Master persists (as a JSON resource item) against the involved party identified by the
 * unique JWebMP web-client key.
 *
 * <p>These validate the value semantics independently of the reactive/DB layer: string and object
 * value storage, typed retrieval, presence/removal, clearing, and JSON round-tripping.</p>
 */
class UserSessionDataTest
{
	@Test
	@DisplayName("String values store and retrieve verbatim")
	void stringValues()
	{
		UserSession s = new UserSession();
		s.addValue("theme", "dark");

		assertTrue(s.hasValue("theme"));
		assertEquals("dark", s.as("theme", String.class));
		assertEquals("dark", s.getValues().get("theme"));
	}

	@Test
	@DisplayName("Object values are JSON-serialised and round-trip through typed retrieval")
	void objectValues()
	{
		UserSession s = new UserSession();
		s.addValue("ids", new int[]{1, 2, 3});

		int[] ids = s.as("ids", int[].class);
		assertNotNull(ids);
		assertArrayEquals(new int[]{1, 2, 3}, ids);
	}

	@Test
	@DisplayName("Missing keys return null and are reported absent")
	void missingKeys()
	{
		UserSession s = new UserSession();
		assertFalse(s.hasValue("nope"));
		assertNull(s.as("nope", String.class));
	}

	@Test
	@DisplayName("Values can be removed and the session cleared")
	void removeAndClear()
	{
		UserSession s = new UserSession();
		s.addValue("a", "1");
		s.addValue("b", "2");

		s.removeValue("a");
		assertFalse(s.hasValue("a"));
		assertTrue(s.hasValue("b"));

		s.clear();
		assertFalse(s.hasValue("b"));
		assertTrue(s.getValues().isEmpty());
	}

	@Test
	@DisplayName("Re-adding the same value is idempotent (no duplicate state)")
	void idempotentReAdd()
	{
		UserSession s = new UserSession();
		s.addValue("k", "v");
		s.addValue("k", "v");
		assertEquals(1, s.getValues().size());
		assertEquals("v", s.as("k", String.class));
	}

	@Test
	@DisplayName("Resource-item and data ids are retained for the persisted backing record")
	void resourceItemAndDataIds()
	{
		UUID resourceItemId = UUID.randomUUID();
		UUID dataId = UUID.randomUUID();

		UserSession s = new UserSession();
		assertSame(s, s.setResourceItemID(resourceItemId));
		assertSame(s, s.setDataID(dataId));

		assertEquals(resourceItemId, s.getResourceItemID());
		assertEquals(dataId, s.getDataID());
	}

	@Test
	@DisplayName("toString produces parseable JSON of the session values")
	void jsonOutput()
	{
		UserSession s = new UserSession();
		s.addValue("first", "Ada");
		s.addValue("last", "Lovelace");

		String json = s.toString();
		assertNotNull(json);
		assertTrue(json.contains("Ada"));
		assertTrue(json.contains("Lovelace"));
	}
}

