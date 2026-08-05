package com.example.ai_companion.livestream;

import java.util.List;
import java.util.UUID;

/** Durable, bounded configuration for one player's AI livestream commentary. */
public record LivestreamSession(UUID playerId, String playerName, List<String> viewers,
		int intervalTicks, long commentsGenerated, boolean enabled) {
	public static final int MIN_INTERVAL_TICKS = 200;
	public static final int MAX_INTERVAL_TICKS = 12_000;
	public static final int MAX_VIEWERS = 16;

	public LivestreamSession {
		if (playerId == null) throw new IllegalArgumentException("Missing player UUID");
		playerName = bounded(playerName, 16);
		if (playerName.isBlank()) throw new IllegalArgumentException("Missing player name");
		viewers = viewers == null ? List.of() : viewers.stream()
			.map(value -> bounded(value, 16))
			.filter(value -> value.matches("[A-Za-z0-9_]{3,16}"))
			.distinct().limit(MAX_VIEWERS).toList();
		if (viewers.isEmpty()) throw new IllegalArgumentException("At least one AI viewer is required");
		intervalTicks = Math.clamp(intervalTicks, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
		commentsGenerated = Math.max(0, commentsGenerated);
	}

	public LivestreamSession withViewers(List<String> value) {
		return new LivestreamSession(playerId, playerName, value, intervalTicks, commentsGenerated, enabled);
	}

	public LivestreamSession withIntervalTicks(int value) {
		return new LivestreamSession(playerId, playerName, viewers, value, commentsGenerated, enabled);
	}

	public LivestreamSession withEnabled(boolean value) {
		return new LivestreamSession(playerId, playerName, viewers, intervalTicks, commentsGenerated, value);
	}

	public LivestreamSession afterComment() {
		return new LivestreamSession(playerId, playerName, viewers, intervalTicks,
			commentsGenerated + 1, enabled);
	}

	public String nextViewer() {
		return viewers.get((int) (commentsGenerated % viewers.size()));
	}

	private static String bounded(String value, int max) {
		String safe = value == null ? "" : value.strip();
		return safe.length() > max ? safe.substring(0, max) : safe;
	}
}
