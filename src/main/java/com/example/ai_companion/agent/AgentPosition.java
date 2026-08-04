package com.example.ai_companion.agent;

import java.util.Locale;

/** Immutable server-side snapshot of an AI player's current location. */
public record AgentPosition(String name, String dimension, double x, double y, double z) {
	public AgentPosition {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("AI name cannot be blank");
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("Dimension cannot be blank");
		}
	}

	/** Returns a stable, chat-friendly representation independent of the server locale. */
	public String displayText() {
		return String.format(Locale.ROOT, "%s · %s · X %.1f, Y %.1f, Z %.1f",
			name, dimension, x, y, z);
	}
}
