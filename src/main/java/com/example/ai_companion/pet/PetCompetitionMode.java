package com.example.ai_companion.pet;

import java.util.Locale;

/** Supported server-authoritative pet competition modes. */
public enum PetCompetitionMode {
	RACE("竞速"), BATTLE("战斗");

	private final String displayName;

	PetCompetitionMode(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public static PetCompetitionMode parse(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "race", "竞速" -> RACE;
			case "battle", "战斗" -> BATTLE;
			default -> throw new IllegalArgumentException("比赛类型必须是 race 或 battle");
		};
	}
}
