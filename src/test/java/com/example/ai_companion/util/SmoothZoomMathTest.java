package com.example.ai_companion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmoothZoomMathTest {
	@Test
	void immediateTransitionUsesTarget() {
		assertEquals(4.0, SmoothZoomMath.approach(1.0, 4.0, 0.016, 0.0));
	}

	@Test
	void smoothTransitionApproachesWithoutOvershoot() {
		double next = SmoothZoomMath.approach(1.0, 4.0, 0.016, 0.18);
		assertTrue(next > 1.0 && next < 4.0);
		assertTrue(SmoothZoomMath.approach(next, 4.0, 0.016, 0.18) > next);
	}
}
