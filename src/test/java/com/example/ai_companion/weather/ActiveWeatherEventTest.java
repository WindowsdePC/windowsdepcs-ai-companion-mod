package com.example.ai_companion.weather;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ActiveWeatherEventTest {
	@Test void countdownNeverBecomesNegative() {
		ActiveWeatherEvent event = new ActiveWeatherEvent(WeatherEventType.AURORA, 1, 1200, false).nextTick();
		assertTrue(event.expired()); assertEquals(0, event.remainingTicks());
	}

	@Test void aliasesAndLimitsAreStable() {
		assertEquals(WeatherEventType.METEOR_SHOWER, WeatherEventType.parse("meteor"));
		assertEquals(WeatherEventType.ENHANCED_THUNDERSTORM, WeatherEventType.parse("thunder"));
		assertThrows(IllegalArgumentException.class, () -> new ActiveWeatherEvent(WeatherEventType.SANDSTORM, 1, 20L * 60L * 31L, false));
	}
}
