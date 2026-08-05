package com.example.ai_companion.navigation;

/** Pure navigation calculations shared by the HUD and unit tests. */
public final class NavigationMath {
	private NavigationMath() {
	}

	public static double horizontalDistance(double x, double z, double targetX, double targetZ) {
		return Math.hypot(targetX - x, targetZ - z);
	}

	public static double relativeBearing(double x, double z, float yaw, double targetX, double targetZ) {
		double absolute = Math.toDegrees(Math.atan2(-(targetX - x), targetZ - z));
		return wrapDegrees(absolute - yaw);
	}

	public static double progress(double remaining, double starting) {
		if (!Double.isFinite(remaining) || !Double.isFinite(starting) || starting <= 0) return 0;
		return Math.clamp(1.0 - remaining / starting, 0.0, 1.0);
	}

	public static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180) wrapped -= 360;
		if (wrapped < -180) wrapped += 360;
		return wrapped;
	}
}
