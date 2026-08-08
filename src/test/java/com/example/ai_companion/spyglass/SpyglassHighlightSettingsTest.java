package com.example.ai_companion.spyglass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpyglassHighlightSettingsTest {
	@Test
	void defaultsUseTenSecondCooldown() {
		assertEquals(200, SpyglassHighlightSettings.defaults().cooldownTicks());
	}

	@Test
	void missingLegacyCooldownMigratesToDefault() {
		var legacy = new SpyglassHighlightSettings(true, 10, 20, 2400,
			SpyglassTargetCondition.ALL_LIVING, 0);
		assertEquals(200, legacy.normalized().cooldownTicks());
	}

	@Test
	void cooldownIsBounded() {
		assertEquals(20, SpyglassHighlightSettings.defaults().withCooldownSeconds(1).cooldownTicks());
		assertEquals(12000, SpyglassHighlightSettings.defaults().withCooldownSeconds(600).cooldownTicks());
	}
}
