package com.example.ai_companion.pet;

import java.util.Locale;

/** Trainable attributes used by AI pet competitions. */
public enum PetAttribute {
	SPEED("速度"), STRENGTH("力量"), ENDURANCE("耐力");

	private final String displayName;

	PetAttribute(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public static PetAttribute parse(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "speed", "速度" -> SPEED;
			case "strength", "力量" -> STRENGTH;
			case "endurance", "耐力" -> ENDURANCE;
			default -> throw new IllegalArgumentException("属性必须是 speed、strength 或 endurance");
		};
	}
}
