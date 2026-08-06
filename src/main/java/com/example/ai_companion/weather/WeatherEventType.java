package com.example.ai_companion.weather;

import java.util.Locale;

/** Supported server-authoritative natural events. */
public enum WeatherEventType {
	AURORA("极光", true),
	METEOR_SHOWER("流星雨", true),
	SANDSTORM("沙尘暴", false),
	ENHANCED_THUNDERSTORM("增强雷暴", false);

	private final String displayName;
	private final boolean nightOnly;

	WeatherEventType(String displayName, boolean nightOnly) {
		this.displayName = displayName;
		this.nightOnly = nightOnly;
	}

	public String displayName() { return displayName; }
	public boolean nightOnly() { return nightOnly; }

	public static WeatherEventType parse(String value) {
		String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
		return switch (normalized) {
			case "aurora" -> AURORA;
			case "meteor", "meteor_shower" -> METEOR_SHOWER;
			case "sandstorm" -> SANDSTORM;
			case "thunder", "enhanced_thunderstorm" -> ENHANCED_THUNDERSTORM;
			default -> throw new IllegalArgumentException("事件必须是 aurora、meteor、sandstorm 或 thunder");
		};
	}
}
