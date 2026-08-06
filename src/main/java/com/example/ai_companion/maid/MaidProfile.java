package com.example.ai_companion.maid;

import java.util.UUID;

/** Durable ownership and appearance data for one AI maid. */
public record MaidProfile(String name, UUID ownerUuid, String ownerName, String skinKey,
		String capeKey, MaidMood mood, boolean stored, int level, int workExperience) {
	public MaidProfile {
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("女仆名称必须为 3-16 位英文字母、数字或下划线");
		}
		if (ownerUuid == null) throw new IllegalArgumentException("女仆所有者不能为空");
		ownerName = ownerName == null ? "" : ownerName.strip();
		skinKey = MaidSkins.validate(skinKey);
		capeKey = MaidSkins.validateOptional(capeKey);
		mood = mood == null ? MaidMood.CALM : mood;
		level = Math.clamp(level, 0, MaidProgression.MAX_LEVEL);
		workExperience = Math.clamp(workExperience, 0, MaidProgression.MAX_WORK_EXPERIENCE);
	}

	/** Compatibility constructor for 0.7.5-0.7.6 profiles and callers. */
	public MaidProfile(String name, UUID ownerUuid, String ownerName, String skinKey,
			String capeKey, MaidMood mood, boolean stored) {
		this(name, ownerUuid, ownerName, skinKey, capeKey, mood, stored, 0, 0);
	}

	public MaidProfile withMood(MaidMood next) {
		return new MaidProfile(name, ownerUuid, ownerName, skinKey, capeKey, next, stored,
			level, workExperience);
	}

	public MaidProfile withStored(boolean next) {
		return new MaidProfile(name, ownerUuid, ownerName, skinKey, capeKey, mood, next,
			level, workExperience);
	}

	public MaidProfile withOwner(UUID uuid, String newOwnerName) {
		return new MaidProfile(name, uuid, newOwnerName, skinKey, capeKey, mood, stored,
			level, workExperience);
	}

	public MaidProfile withProgress(int nextLevel, int nextWorkExperience) {
		return new MaidProfile(name, ownerUuid, ownerName, skinKey, capeKey, mood, stored,
			nextLevel, nextWorkExperience);
	}

	public MaidProfile addWorkExperience(int amount) {
		return withProgress(level, (int) Math.clamp((long) workExperience + Math.max(0, amount),
			0L, (long) MaidProgression.MAX_WORK_EXPERIENCE));
	}
}
