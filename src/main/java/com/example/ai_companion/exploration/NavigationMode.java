package com.example.ai_companion.exploration;

/** Player-selected behavior after a biome or structure target is located. */
public enum NavigationMode {
	NAVIGATE("navigate", "导航"),
	TELEPORT("teleport", "传送");

	private final String id;
	private final String label;

	NavigationMode(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	public static NavigationMode parse(String value) {
		for (NavigationMode mode : values()) {
			if (mode.id.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) return mode;
		}
		throw new IllegalArgumentException("导航模式必须是 navigate 或 teleport");
	}
}
