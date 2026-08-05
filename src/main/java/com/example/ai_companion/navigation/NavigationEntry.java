package com.example.ai_companion.navigation;

/** One searchable server registry entry exposed to the navigator UI. */
public record NavigationEntry(String type, String id) {
	public static final int MAX_TYPE_LENGTH = 16;
	public static final int MAX_ID_LENGTH = 160;

	public NavigationEntry {
		if (!type.matches("biome|structure|dimension|special")) {
			throw new IllegalArgumentException("Unsupported navigation type: " + type);
		}
		if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) {
			throw new IllegalArgumentException("Invalid navigation id");
		}
	}
}
