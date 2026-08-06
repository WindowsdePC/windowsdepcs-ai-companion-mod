package com.example.ai_companion.pet;

import java.util.UUID;

/** Durable, bounded attributes and results for one player-owned AI pet. */
public record PetProfile(UUID ownerId, String name, int speed, int strength, int endurance,
		int trainingPoints, int raceWins, int battleWins, int competitions, long lastTrainingEpochMillis) {
	public static final int MIN_ATTRIBUTE = 1;
	public static final int MAX_ATTRIBUTE = 100;

	public PetProfile {
		if (ownerId == null) throw new IllegalArgumentException("ownerId is required");
		name = validateName(name);
		speed = Math.clamp(speed, MIN_ATTRIBUTE, MAX_ATTRIBUTE);
		strength = Math.clamp(strength, MIN_ATTRIBUTE, MAX_ATTRIBUTE);
		endurance = Math.clamp(endurance, MIN_ATTRIBUTE, MAX_ATTRIBUTE);
		trainingPoints = Math.max(0, trainingPoints);
		raceWins = Math.max(0, raceWins);
		battleWins = Math.max(0, battleWins);
		competitions = Math.max(0, competitions);
		lastTrainingEpochMillis = Math.max(0L, lastTrainingEpochMillis);
	}

	public static PetProfile create(UUID ownerId, String name) {
		return new PetProfile(ownerId, name, 40, 40, 40, 0, 0, 0, 0, 0L);
	}

	public PetProfile train(PetAttribute attribute, long now) {
		if (now - lastTrainingEpochMillis < 30_000L) {
			throw new IllegalStateException("同一只宠物每 30 秒只能训练一次");
		}
		return new PetProfile(ownerId, name,
			attribute == PetAttribute.SPEED ? speed + 1 : speed,
			attribute == PetAttribute.STRENGTH ? strength + 1 : strength,
			attribute == PetAttribute.ENDURANCE ? endurance + 1 : endurance,
			trainingPoints + 1, raceWins, battleWins, competitions, now);
	}

	public PetProfile recordRace(boolean won) {
		return new PetProfile(ownerId, name, speed, strength, endurance, trainingPoints,
			raceWins + (won ? 1 : 0), battleWins, competitions + 1, lastTrainingEpochMillis);
	}

	public PetProfile recordBattle(boolean won) {
		return new PetProfile(ownerId, name, speed, strength, endurance, trainingPoints,
			raceWins, battleWins + (won ? 1 : 0), competitions + 1, lastTrainingEpochMillis);
	}

	public String displayText() {
		return "%s · 速度 %d · 力量 %d · 耐力 %d · 竞速胜 %d · 战斗胜 %d · 参赛 %d"
			.formatted(name, speed, strength, endurance, raceWins, battleWins, competitions);
	}

	private static String validateName(String value) {
		String normalized = value == null ? "" : value.strip();
		if (!normalized.matches("[A-Za-z0-9_\\-]{1,24}")) {
			throw new IllegalArgumentException("宠物名称须为 1～24 位字母、数字、下划线或连字符");
		}
		return normalized;
	}
}
