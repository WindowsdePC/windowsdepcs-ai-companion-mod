package com.example.ai_companion.maid;

import java.util.UUID;

/** Durable ownership and appearance data for one AI maid. */
public record MaidProfile(String name, UUID ownerUuid, String ownerName, String skinKey,
		String capeKey, MaidMood mood, boolean stored) {
	public MaidProfile {
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("女仆名称必须为 3-16 位英文字母、数字或下划线");
		}
		if (ownerUuid == null) throw new IllegalArgumentException("女仆所有者不能为空");
		ownerName = ownerName == null ? "" : ownerName.strip();
		skinKey = MaidSkins.validate(skinKey);
		capeKey = MaidSkins.validateOptional(capeKey);
		mood = mood == null ? MaidMood.CALM : mood;
	}

	public MaidProfile withMood(MaidMood next) {
		return new MaidProfile(name, ownerUuid, ownerName, skinKey, capeKey, next, stored);
	}

	public MaidProfile withStored(boolean next) {
		return new MaidProfile(name, ownerUuid, ownerName, skinKey, capeKey, mood, next);
	}
}
