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

	public static boolean offCourse(double relativeBearing) {
		return Double.isFinite(relativeBearing) && Math.abs(wrapDegrees(relativeBearing)) >= 60.0;
	}

	public static String cardinalDirection(double x, double z, double targetX, double targetZ) {
		double angle = Math.toDegrees(Math.atan2(targetZ - z, targetX - x));
		if (angle < 0) angle += 360;
		if (angle < 22.5 || angle >= 337.5) return "东";
		if (angle < 67.5) return "东南";
		if (angle < 112.5) return "南";
		if (angle < 157.5) return "西南";
		if (angle < 202.5) return "西";
		if (angle < 247.5) return "西北";
		if (angle < 292.5) return "北";
		return "东北";
	}

	public static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180) wrapped -= 360;
		if (wrapped < -180) wrapped += 360;
		return wrapped;
	}
}
