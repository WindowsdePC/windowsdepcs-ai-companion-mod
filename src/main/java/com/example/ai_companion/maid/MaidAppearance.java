package com.example.ai_companion.maid;

import java.util.UUID;

/** Bounded appearance snapshot sent to clients for player-model skin replacement. */
public record MaidAppearance(UUID entityUuid, String name, String skinKey, String capeKey, MaidMood mood) {
	public MaidAppearance {
		if (entityUuid == null) throw new IllegalArgumentException("entityUuid");
		if (name == null || name.length() > 16) throw new IllegalArgumentException("maid name");
		skinKey = MaidSkins.validate(skinKey);
		capeKey = MaidSkins.validateOptional(capeKey);
		mood = mood == null ? MaidMood.CALM : mood;
	}
}
