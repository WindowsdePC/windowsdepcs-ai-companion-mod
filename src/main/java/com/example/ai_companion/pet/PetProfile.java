package com.example.ai_companion.pet;

import java.util.Objects;
import java.util.UUID;

/** Persistent owner-bound AI pet with bounded competition attributes. */
public record PetProfile(UUID ownerId, String ownerName, String name, int speed, int strength,
		int endurance, int wins, int losses, int races, long trainingCount, long lastTrainingMillis) {
	public static final int MIN_ATTRIBUTE = 10;
	public static final int MAX_ATTRIBUTE = 100;
	public static final int MAX_INITIAL_TOTAL = 180;

	public PetProfile {
		Objects.requireNonNull(ownerId, "ownerId");
		ownerName = requireText(ownerName, 16, "主人名称");
		name = requireText(name, 24, "宠物名称");
		validateAttribute(speed);
		validateAttribute(strength);
		validateAttribute(endurance);
		if (wins < 0 || losses < 0 || races < 0 || trainingCount < 0 || lastTrainingMillis < 0) {
			throw new IllegalArgumentException("宠物统计不能为负数");
		}
	}

	public static PetProfile create(UUID ownerId, String ownerName, String name,
			int speed, int strength, int endurance) {
		if (speed + strength + endurance > MAX_INITIAL_TOTAL) {
			throw new IllegalArgumentException("初始速度、力量和耐力总和不能超过 " + MAX_INITIAL_TOTAL);
		}
		return new PetProfile(ownerId, ownerName, name, speed, strength, endurance, 0, 0, 0, 0, 0);
	}

	public PetProfile train(PetAttribute attribute, long nowMillis) {
		if (nowMillis < lastTrainingMillis + 30_000L) {
			throw new IllegalStateException("训练冷却尚未结束");
		}
		int nextSpeed = speed;
		int nextStrength = strength;
		int nextEndurance = endurance;
		switch (attribute) {
			case SPEED -> nextSpeed = improve(speed);
			case STRENGTH -> nextStrength = improve(strength);
			case ENDURANCE -> nextEndurance = improve(endurance);
		}
		return new PetProfile(ownerId, ownerName, name, nextSpeed, nextStrength, nextEndurance,
			wins, losses, races, trainingCount + 1, nowMillis);
	}

	public PetProfile recordResult(boolean won) {
		return new PetProfile(ownerId, ownerName, name, speed, strength, endurance,
			wins + (won ? 1 : 0), losses + (won ? 0 : 1), races + 1, trainingCount, lastTrainingMillis);
	}

	public int rating() {
		return speed + strength + endurance + wins * 3 - losses;
	}

	private static int improve(int value) {
		if (value >= MAX_ATTRIBUTE) throw new IllegalStateException("该属性已经达到上限");
		return value + 1;
	}

	private static void validateAttribute(int value) {
		if (value < MIN_ATTRIBUTE || value > MAX_ATTRIBUTE) {
			throw new IllegalArgumentException("宠物属性必须在 " + MIN_ATTRIBUTE + "～" + MAX_ATTRIBUTE + " 之间");
		}
	}

	private static String requireText(String value, int maximum, String field) {
		if (value == null || value.isBlank() || value.length() > maximum) {
			throw new IllegalArgumentException(field + "不能为空且不能超过 " + maximum + " 字符");
		}
		return value.strip();
	}
}
