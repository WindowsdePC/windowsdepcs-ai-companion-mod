package com.example.ai_companion.pet;

import java.util.Locale;

public enum PetAttribute {
	SPEED, STRENGTH, ENDURANCE;

	public static PetAttribute parse(String value) {
		try {
			return valueOf(value.strip().toUpperCase(Locale.ROOT));
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("属性只能是 speed、strength 或 endurance");
		}
	}
}
