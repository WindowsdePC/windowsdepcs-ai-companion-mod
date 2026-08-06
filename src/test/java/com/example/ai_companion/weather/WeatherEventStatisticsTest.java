package com.example.ai_companion.weather;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WeatherEventStatisticsTest {
	@Test
	void aggregatesAutomaticAdministratorAndDuration() {
		var value = WeatherEventStatistics.from(List.of(
			new WeatherEventRecord(WeatherEventType.AURORA, 1, 300, true),
			new WeatherEventRecord(WeatherEventType.SANDSTORM, 2, 600, false),
			new WeatherEventRecord(WeatherEventType.METEOR_SHOWER, 3, 180, true)));
		assertEquals(3, value.events());
		assertEquals(2, value.automaticEvents());
		assertEquals(1, value.administratorEvents());
		assertEquals(1080, value.plannedDurationSeconds());
	}
}
