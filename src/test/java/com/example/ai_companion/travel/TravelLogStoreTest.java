package com.example.ai_companion.travel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TravelLogStoreTest {
	@TempDir
	Path directory;

	@Test
	void discoveriesAreDeduplicatedAndSurviveReload() throws Exception {
		Path file = directory.resolve("travel.json");
		UUID player = UUID.randomUUID();
		TravelLogStore first = TravelLogStore.load(file);
		TravelLogEntry entry = first.addIfAbsent(player, TravelLogCategory.BIOME,
			"biome:minecraft:overworld:minecraft:plains", "minecraft:plains",
			"minecraft:overworld", 10, 64, -8, 100);
		assertNotNull(entry);
		assertNull(first.addIfAbsent(player, TravelLogCategory.BIOME,
			"biome:minecraft:overworld:minecraft:plains", "minecraft:plains",
			"minecraft:overworld", 20, 70, 4, 200));
		first.linkPhoto(player, entry.id(), 7);

		TravelLogStore second = TravelLogStore.load(file);
		TravelLogEntry loaded = second.require(player, entry.id());
		assertEquals(7, loaded.photoId());
		assertEquals(1, second.entries(player).size());
		assertEquals(1L, second.categoryCounts(player).get(TravelLogCategory.BIOME));
	}

	@Test
	void invalidEntryMetadataIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new TravelLogEntry(1,
			TravelLogCategory.SPECIAL, "key", "name", "minecraft:overworld",
			Double.NaN, 0, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new TravelLogEntry(1,
			TravelLogCategory.SPECIAL, "key", "name", "minecraft:overworld",
			0, 0, 0, 0, -1));
	}
}
