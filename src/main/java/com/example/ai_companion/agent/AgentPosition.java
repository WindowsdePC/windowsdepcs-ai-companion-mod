package com.example.ai_companion.agent;

import java.util.Locale;

/** Immutable server-side snapshot displayed by the F8 AI console. */
public record AgentPosition(String name, String dimension, double x, double y, double z,
		AgentMode mode, String lastMessage) {
	public AgentPosition(String name, String dimension, double x, double y, double z) {
		this(name, dimension, x, y, z, AgentMode.SURVIVAL, "");
	}

	public AgentPosition {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("AI name cannot be blank");
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("Dimension cannot be blank");
		}
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("AI coordinates must be finite");
		}
		mode = mode == null ? AgentMode.SURVIVAL : mode;
		lastMessage = lastMessage == null ? "" : lastMessage.strip();
		if (lastMessage.length() > 512) lastMessage = lastMessage.substring(0, 512);
	}

	/** Returns a stable, chat-friendly representation independent of the server locale. */
	public String displayText() {
		return String.format(Locale.ROOT, "%s [%s] · %s · X %.1f, Y %.1f, Z %.1f",
			name, mode.name().toLowerCase(Locale.ROOT), dimension, x, y, z);
	}
}
