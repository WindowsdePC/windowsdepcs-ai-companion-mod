package com.example.ai_companion.util;

/** Time-based interpolation used by the client zoom without changing the saved vanilla FOV. */
public final class SmoothZoomMath {
	private SmoothZoomMath() {
	}

	public static double approach(double current, double target, double elapsedSeconds,
			double transitionSeconds) {
		current = Math.max(1.0, current);
		target = Math.max(1.0, target);
		if (transitionSeconds <= 0.001) return target;
		double elapsed = Math.clamp(elapsedSeconds, 0.0, 0.25);
		double blend = 1.0 - Math.exp(-elapsed * 4.0 / transitionSeconds);
		double result = current + (target - current) * blend;
		return Math.abs(result - target) < 0.001 ? target : result;
	}
}
