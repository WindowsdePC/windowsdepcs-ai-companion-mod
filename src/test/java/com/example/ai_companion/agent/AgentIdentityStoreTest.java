package com.example.ai_companion.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AgentIdentityStoreTest {
	@TempDir Path temporaryDirectory;

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

	@Test void isolatesPersistentIdentitiesByWorldDirectory() throws Exception {
		Path firstWorld = temporaryDirectory.resolve("world-one");
		Path secondWorld = temporaryDirectory.resolve("world-two");
		AgentIdentityStore.StoredAgent stored = new AgentIdentityStore.StoredAgent("World_AI",
			UUID.randomUUID().toString(), "minecraft:overworld", 4, 70, -8, "IDLE", "", "", "", "", 0,
			false, 200);

		AgentIdentityStore.loadForWorld(firstWorld).replace(List.of(stored));

		assertEquals(1, AgentIdentityStore.loadForWorld(firstWorld).entries().size());
		assertEquals(0, AgentIdentityStore.loadForWorld(secondWorld).entries().size());
		assertEquals(firstWorld.toAbsolutePath().normalize().resolve("data")
			.resolve("windowsdepcs-ai-companion-agent-identities.json"),
			AgentIdentityStore.worldFile(firstWorld));
	}
}
