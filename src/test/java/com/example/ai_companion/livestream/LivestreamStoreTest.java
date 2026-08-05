package com.example.ai_companion.livestream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LivestreamStoreTest {
	@TempDir Path directory;

	@Test
	void sessionPersistsAndRotatesViewers() throws Exception {
		Path file = directory.resolve("live.json");
		UUID player = UUID.randomUUID();
		LivestreamStore first = LivestreamStore.load(file);
		LivestreamSession session = first.put(new LivestreamSession(player, "Player",
			List.of("Alex_AI", "Steve_AI"), 600, 0, true));
		assertEquals("Alex_AI", session.nextViewer());
		first.put(session.afterComment());

		LivestreamStore second = LivestreamStore.load(file);
		assertEquals("Steve_AI", second.require(player).nextViewer());
		assertTrue(second.require(player).enabled());
	}

	@Test
	void fieldsAndCommentTextAreBounded() {
		UUID player = UUID.randomUUID();
		assertThrows(IllegalArgumentException.class, () -> new LivestreamSession(player,
			"Player", List.of(), 600, 0, true));
		LivestreamSession session = new LivestreamSession(player, "Player",
			List.of("Alex_AI"), 1, -5, true);
		assertEquals(LivestreamSession.MIN_INTERVAL_TICKS, session.intervalTicks());
		assertEquals(0, session.commentsGenerated());
		assertEquals(81, LivestreamManager.sanitizeComment("x".repeat(100)).length());
	}
}
