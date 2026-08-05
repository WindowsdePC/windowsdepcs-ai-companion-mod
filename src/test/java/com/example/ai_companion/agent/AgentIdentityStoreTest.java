package com.example.ai_companion.agent;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AgentIdentityStoreTest {
	@Test void normalizesDurableIdentity() {
		UUID uuid = UUID.randomUUID();
		AgentIdentityStore.StoredAgent stored = new AgentIdentityStore.StoredAgent("Helper_1", uuid.toString(),
			"minecraft:overworld", 1, 64, -2, "HUNTER", "Steve", "idle", "", "", 4,
			true, 400).normalized();
		assertEquals(uuid.toString(), stored.uuid());
		assertEquals(AgentMode.HUNTER.name(), stored.mode());
		assertEquals(400, stored.automaticIntervalTicks());
	}

	@Test void rejectsInvalidCoordinatesAndNames() {
		assertThrows(IllegalArgumentException.class, () -> new AgentIdentityStore.StoredAgent("bad name",
			UUID.randomUUID().toString(), "minecraft:overworld", 0, 64, 0, "IDLE", "", "", "", "", 0,
			false, 200)
			.normalized());
		assertThrows(IllegalArgumentException.class, () -> new AgentIdentityStore.StoredAgent("Good_AI",
			UUID.randomUUID().toString(), "minecraft:overworld", Double.NaN, 64, 0, "IDLE", "", "", "", "", 0,
			false, 200)
			.normalized());
	}

	@Test void migratesMissingAutomaticIntervalToTenSeconds() {
		AgentIdentityStore.StoredAgent stored = new AgentIdentityStore.StoredAgent("Helper_2",
			UUID.randomUUID().toString(), "minecraft:overworld", 0, 64, 0, "IDLE", "", "", "", "", 0,
			false, 0).normalized();
		assertEquals(200, stored.automaticIntervalTicks());
	}
}
