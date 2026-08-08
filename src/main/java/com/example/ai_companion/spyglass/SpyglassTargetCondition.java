package com.example.ai_companion.spyglass;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Optional target filtering for the spyglass pulse. */
public enum SpyglassTargetCondition {
	ALL_LIVING("全部生物"),
	NON_PLAYERS("仅非玩家生物"),
	HOSTILE_ONLY("仅敌对生物");

	private final String displayName;
	SpyglassTargetCondition(String displayName) { this.displayName = displayName; }
	public String displayName() { return displayName; }
	public boolean matches(LivingEntity entity) {
		return switch (this) {
			case ALL_LIVING -> true;
			case NON_PLAYERS -> !(entity instanceof Player);
			case HOSTILE_ONLY -> entity instanceof Monster;
		};
	}
	public static SpyglassTargetCondition parse(String value) {
		try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
		catch (RuntimeException ignored) { return ALL_LIVING; }
	}
}
