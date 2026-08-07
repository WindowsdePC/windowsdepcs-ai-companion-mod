package com.example.ai_companion.weather;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for automatic-event cooldowns and repeat prevention. */
public final class WeatherAutomaticPolicy {
	private WeatherAutomaticPolicy() { }

	public static int remainingCooldownSeconds(List<WeatherEventRecord> history, int cooldownMinutes, long nowMillis) {
		if (cooldownMinutes <= 0 || history == null) return 0;
		for (WeatherEventRecord record : history) {
			if (record == null || !record.automatic()) continue;
			long end = record.startedAtEpochMillis() + cooldownMinutes * 60_000L;
			long remaining = end - nowMillis;
			if (remaining <= 0) return 0;
			return (int) Math.min(Integer.MAX_VALUE, (remaining + 999L) / 1000L);
		}
		return 0;
	}

	public static WeatherEventType mostRecentAutomaticType(List<WeatherEventRecord> history) {
		if (history == null) return null;
		for (WeatherEventRecord record : history)
			if (record != null && record.automatic()) return record.type();
		return null;
	}

	public static List<WeatherEventType> avoidImmediateRepeat(List<WeatherEventType> candidates,
		WeatherEventType previous) {
		if (candidates == null || candidates.size() < 2 || previous == null) return candidates == null ? List.of() : List.copyOf(candidates);
		List<WeatherEventType> filtered = new ArrayList<>(candidates);
		filtered.removeIf(type -> type == previous);
		return filtered.isEmpty() ? List.copyOf(candidates) : List.copyOf(filtered);
	}
}
