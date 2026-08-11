package com.example.ai_companion.agent;

import java.util.Locale;

/** Immutable server-side snapshot displayed by the F8 AI console. */
public record AgentPosition(String name, String uuid, String dimension, double x, double y, double z,
		float health, float maxHealth, String gameMode, AgentMode mode, String targetName,
		String promptId, boolean automaticEnabled, String activeTask, String lastMessage) {
	public AgentPosition(String name, String dimension, double x, double y, double z) {
		this(name, "00000000-0000-0000-0000-000000000000", dimension, x, y, z,
			20.0F, 20.0F, "survival", AgentMode.SURVIVAL, "", "", false, "", "");
	}

	public AgentPosition(String name, String dimension, double x, double y, double z,
			AgentMode mode, String lastMessage) {
		this(name, "00000000-0000-0000-0000-000000000000", dimension, x, y, z,
			20.0F, 20.0F, "survival", mode, "", "", false, "", lastMessage);
	}

	public AgentPosition {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("AI name cannot be blank");
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("Dimension cannot be blank");
		}
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("AI coordinates must be finite");
		}
		if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("AI UUID cannot be blank");
		if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
			throw new IllegalArgumentException("AI health must be finite");
		}
		gameMode = bounded(gameMode, 24);
		mode = mode == null ? AgentMode.SURVIVAL : mode;
		targetName = bounded(targetName, 16);
		promptId = bounded(promptId, 64);
		activeTask = bounded(activeTask, 500);
		lastMessage = bounded(lastMessage, 512);
	}

	/** Returns a stable, chat-friendly representation independent of the server locale. */
	public String displayText() {
		return String.format(Locale.ROOT, "%s [%s] · %s · X %.1f, Y %.1f, Z %.1f",
			name, mode.name().toLowerCase(Locale.ROOT), dimension, x, y, z);
	}

	private static String bounded(String value, int maximum) {
		String safe = value == null ? "" : value.strip();
		return safe.length() <= maximum ? safe : safe.substring(0, maximum);
	}
}
