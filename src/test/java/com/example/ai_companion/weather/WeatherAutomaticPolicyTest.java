package com.example.ai_companion.weather;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherAutomaticPolicyTest {
	@Test void cooldownUsesMostRecentAutomaticEventOnly() {
		long now = 1_000_000L;
		var manual = new WeatherEventRecord(WeatherEventType.AURORA, now - 1_000L, 60, false);
		var automatic = new WeatherEventRecord(WeatherEventType.METEOR_SHOWER, now - 60_000L, 60, true);
		assertEquals(540, WeatherAutomaticPolicy.remainingCooldownSeconds(List.of(manual, automatic), 10, now));
	}

	@Test void repeatIsRemovedOnlyWhenAnotherCandidateExists() {
		assertEquals(List.of(WeatherEventType.SANDSTORM), WeatherAutomaticPolicy.avoidImmediateRepeat(
			List.of(WeatherEventType.AURORA, WeatherEventType.SANDSTORM), WeatherEventType.AURORA));
		assertEquals(List.of(WeatherEventType.AURORA), WeatherAutomaticPolicy.avoidImmediateRepeat(
			List.of(WeatherEventType.AURORA), WeatherEventType.AURORA));
	}
}
