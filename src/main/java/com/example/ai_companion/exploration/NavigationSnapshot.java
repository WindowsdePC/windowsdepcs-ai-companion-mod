package com.example.ai_companion.exploration;

/** Immutable server-authoritative HUD state for one active navigation session. */
public record NavigationSnapshot(boolean active, NavigationMode mode, NavigationTargetType targetType,
		String targetId, String dimension, double x, double y, double z, double distance,
		double initialDistance, float relativeBearing, double verticalDifference) {
	public NavigationSnapshot {
		mode = mode == null ? NavigationMode.NAVIGATE : mode;
		targetType = targetType == null ? NavigationTargetType.BIOME : targetType;
		targetId = bounded(targetId, 160);
		dimension = bounded(dimension, 160);
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
				|| !Double.isFinite(distance) || !Double.isFinite(initialDistance)
				|| !Float.isFinite(relativeBearing) || !Double.isFinite(verticalDifference)) {
			throw new IllegalArgumentException("导航数据必须是有限数值");
		}
		distance = Math.max(0.0, distance);
		initialDistance = Math.max(1.0, initialDistance);
	}

	public static NavigationSnapshot inactive() {
		return new NavigationSnapshot(false, NavigationMode.NAVIGATE, NavigationTargetType.BIOME,
			"", "", 0, 0, 0, 0, 1, 0, 0);
	}

	public double progress() {
		return Math.clamp(1.0 - distance / initialDistance, 0.0, 1.0);
	}

	private static String bounded(String value, int maximum) {
		String normalized = value == null ? "" : value.strip();
		if (normalized.length() > maximum) throw new IllegalArgumentException("导航文本过长");
		return normalized;
	}
}
