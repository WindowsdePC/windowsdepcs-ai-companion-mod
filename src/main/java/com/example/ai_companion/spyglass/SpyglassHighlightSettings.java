package com.example.ai_companion.spyglass;

/** Per-player, server-authoritative spyglass highlight settings. */
public record SpyglassHighlightSettings(boolean enabled, int radiusChunks, int holdTicks,
		int effectTicks, SpyglassTargetCondition targetCondition, int cooldownTicks) {
	public static SpyglassHighlightSettings defaults() {
		return new SpyglassHighlightSettings(true, 10, 20, 20 * 120,
			SpyglassTargetCondition.ALL_LIVING, 20 * 10);
	}

	public SpyglassHighlightSettings normalized() {
		return new SpyglassHighlightSettings(enabled, Math.clamp(radiusChunks, 1, 32),
			Math.clamp(holdTicks, 20, 200), Math.clamp(effectTicks, 20, 20 * 600),
			targetCondition == null ? SpyglassTargetCondition.ALL_LIVING : targetCondition,
			cooldownTicks <= 0 ? 20 * 10 : Math.clamp(cooldownTicks, 20, 20 * 600));
	}

	public SpyglassHighlightSettings withEnabled(boolean value) { return new SpyglassHighlightSettings(value, radiusChunks, holdTicks, effectTicks, targetCondition, cooldownTicks).normalized(); }
	public SpyglassHighlightSettings withRadiusChunks(int value) { return new SpyglassHighlightSettings(enabled, value, holdTicks, effectTicks, targetCondition, cooldownTicks).normalized(); }
	public SpyglassHighlightSettings withHoldSeconds(int value) { return new SpyglassHighlightSettings(enabled, radiusChunks, value * 20, effectTicks, targetCondition, cooldownTicks).normalized(); }
	public SpyglassHighlightSettings withEffectSeconds(int value) { return new SpyglassHighlightSettings(enabled, radiusChunks, holdTicks, value * 20, targetCondition, cooldownTicks).normalized(); }
	public SpyglassHighlightSettings withTargetCondition(SpyglassTargetCondition value) { return new SpyglassHighlightSettings(enabled, radiusChunks, holdTicks, effectTicks, value, cooldownTicks).normalized(); }
	public SpyglassHighlightSettings withCooldownSeconds(int value) { return new SpyglassHighlightSettings(enabled, radiusChunks, holdTicks, effectTicks, targetCondition, value * 20).normalized(); }
}
