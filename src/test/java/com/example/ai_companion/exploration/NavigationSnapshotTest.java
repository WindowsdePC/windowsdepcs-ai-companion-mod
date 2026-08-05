package com.example.ai_companion.exploration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NavigationSnapshotTest {
	@Test
	void progressIsClampedAtBothEnds() {
		NavigationSnapshot half = new NavigationSnapshot(true, NavigationMode.NAVIGATE,
			NavigationTargetType.STRUCTURE, "minecraft:stronghold", "minecraft:overworld",
			100, 64, -100, 50, 100, 0, 4);
		assertEquals(0.5, half.progress(), 0.0001);
		NavigationSnapshot beyond = new NavigationSnapshot(true, NavigationMode.NAVIGATE,
			NavigationTargetType.BIOME, "minecraft:plains", "minecraft:overworld",
			0, 64, 0, 200, 100, 0, 0);
		assertEquals(0.0, beyond.progress(), 0.0001);
	}

	@Test
	void rejectsNonFiniteCoordinates() {
		assertThrows(IllegalArgumentException.class, () -> new NavigationSnapshot(true,
			NavigationMode.NAVIGATE, NavigationTargetType.BIOME, "minecraft:plains",
			"minecraft:overworld", Double.NaN, 64, 0, 1, 2, 0, 0));
	}
}
