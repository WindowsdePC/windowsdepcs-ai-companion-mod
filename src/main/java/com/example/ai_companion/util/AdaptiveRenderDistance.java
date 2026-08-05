package com.example.ai_companion.util;

/** Hysteretic distance policy for optional client-only rendering added by this mod. */
public final class AdaptiveRenderDistance {
	private AdaptiveRenderDistance() {
	}

	public static int next(int current, int minimum, int maximum, int targetFps,
			double measuredFps) {
		minimum = Math.max(16, minimum);
		maximum = Math.max(minimum, maximum);
		current = Math.clamp(current, minimum, maximum);
		if (!Double.isFinite(measuredFps) || measuredFps <= 0.0) return current;
		if (measuredFps < targetFps - 5.0) return Math.max(minimum, current - 8);
		if (measuredFps > targetFps + 10.0) return Math.min(maximum, current + 8);
		return current;
	}
}
