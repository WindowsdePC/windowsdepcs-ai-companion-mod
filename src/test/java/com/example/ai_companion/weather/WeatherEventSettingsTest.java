package com.example.ai_companion.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class WeatherEventSettingsTest {
	@Test void defaultsAreBoundedAndMutableByCopy() {
		WeatherEventSettings defaults = WeatherEventSettings.defaults();
		assertTrue(defaults.automaticEnabled());
		assertEquals(120, defaults.withInterval(120).checkIntervalSeconds());
		assertEquals(50, defaults.withChance(50).chanceDenominator());
		assertEquals(12, defaults.withDuration(6, 12).maxDurationMinutes());
	}

	@Test void rejectsUnsafeAutomaticPolicy() {
		assertThrows(IllegalArgumentException.class, () -> new WeatherEventSettings(true, 1, 240, 5, 10));
		assertThrows(IllegalArgumentException.class, () -> new WeatherEventSettings(true, 60, 0, 5, 10));
		assertThrows(IllegalArgumentException.class, () -> new WeatherEventSettings(true, 60, 240, 12, 5));
	}
}
