package com.example.ai_companion.music;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSessionTest {
	@Test
	void harmonyProducesBoundedTriad() {
		assertEquals(12, MusicStyle.HARMONY.noteFor(12, 0));
		assertEquals(16, MusicStyle.HARMONY.noteFor(12, 1));
		assertEquals(19, MusicStyle.HARMONY.noteFor(12, 2));
		assertTrue(MusicStyle.HARMONY.noteFor(24, 2) <= 24);
	}

	@Test
	void echoStaggersMembersAndBassStaysInRange() {
		assertEquals(4, MusicStyle.ECHO.delayFor(0));
		assertEquals(10, MusicStyle.ECHO.delayFor(3));
		assertTrue(MusicStyle.BASS.noteFor(0, 0) >= 0);
	}

	@Test
	void sessionRejectsDuplicateOrEmptyMembers() {
		UUID owner = UUID.randomUUID();
		assertThrows(IllegalArgumentException.class, () -> new MusicSession(owner, "Player",
			List.of(), MusicStyle.HARMONY, 1, 1, 0));
		assertThrows(IllegalArgumentException.class, () -> new MusicSession(owner, "Player",
			List.of("MusicAI", "musicai"), MusicStyle.HARMONY, 1, 1, 0));
	}

	@Test
	void noteCounterAdvancesWithoutMovingStartTick() {
		MusicSession session = new MusicSession(UUID.randomUUID(), "Player", List.of("MusicAI"),
			MusicStyle.ECHO, 40, 40, 0).afterPlayerNote(52);
		assertEquals(40, session.startedAtTick());
		assertEquals(52, session.lastNoteTick());
		assertEquals(1, session.notesPlayed());
	}
}
