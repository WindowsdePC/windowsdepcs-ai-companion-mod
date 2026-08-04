package com.example.ai_companion.orb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssistantOrbStoreTest {
	@TempDir
	Path directory;

	@Test
	void waypointsAndRemindersSurviveReload() throws Exception {
		Path file = directory.resolve("orbs.json");
		UUID player = UUID.randomUUID();
		AssistantOrbStore first = AssistantOrbStore.load(file);
		first.saveWaypoint(player, new AssistantWaypoint("Home", "minecraft:overworld",
			12.25, 64, -8.75, 100));
		AssistantReminder reminder = first.addReminder(player, 500, "返回基地");

		AssistantOrbStore second = AssistantOrbStore.load(file);
		assertEquals("Home", second.waypoints(player).getFirst().name());
		assertEquals(reminder, second.reminders(player).getFirst());
		assertTrue(second.takeDue(500, Set.of()).isEmpty());
		assertEquals(1, second.reminders(player).size());
		assertTrue(second.takeDue(500, Set.of(player)).stream()
			.anyMatch(value -> value.playerId().equals(player)));
		assertTrue(second.reminders(player).isEmpty());
	}

	@Test
	void waypointNamesAreCaseInsensitiveAndValidated() throws Exception {
		AssistantOrbStore store = AssistantOrbStore.load(directory.resolve("orbs.json"));
		UUID player = UUID.randomUUID();
		store.saveWaypoint(player, new AssistantWaypoint("Home", "minecraft:overworld", 0, 64, 0, 1));
		store.saveWaypoint(player, new AssistantWaypoint("home", "minecraft:the_nether", 1, 70, 2, 2));
		assertEquals(1, store.waypoints(player).size());
		assertTrue(store.removeWaypoint(player, "HOME"));
		assertFalse(store.removeWaypoint(player, "missing"));
		assertThrows(IllegalArgumentException.class,
			() -> new AssistantWaypoint("bad name", "minecraft:overworld", 0, 0, 0, 1));
	}
}
