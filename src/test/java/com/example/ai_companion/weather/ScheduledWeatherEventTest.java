package com.example.ai_companion.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledWeatherEventTest {
	@Test void dueAndNightEligibilityAreIndependent() {
		var aurora = new ScheduledWeatherEvent(1, WeatherEventType.AURORA, 1_000L, 5);
		assertTrue(aurora.due(1_000L));
		assertFalse(aurora.eligible(false));
		assertTrue(aurora.eligible(true));
	}

	@Test void daytimeEventCanRunAtAnyTime() {
		var storm = new ScheduledWeatherEvent(2, WeatherEventType.SANDSTORM, 1_000L, 5);
		assertTrue(storm.eligible(false));
	}
}
