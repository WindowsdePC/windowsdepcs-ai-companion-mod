package com.example.ai_companion.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NavigationMathTest {
	@Test void computesDistanceAndProgress() {
		assertEquals(5.0, NavigationMath.horizontalDistance(0, 0, 3, 4), 0.0001);
		assertEquals(0.5, NavigationMath.progress(50, 100), 0.0001);
		assertEquals(1.0, NavigationMath.progress(-1, 100), 0.0001);
	}

	@Test void bearingUsesMinecraftYawConvention() {
		assertEquals(0.0, NavigationMath.relativeBearing(0, 0, 0, 0, 10), 0.0001);
		assertEquals(-90.0, NavigationMath.relativeBearing(0, 0, 0, 10, 0), 0.0001);
		assertEquals(0.0, NavigationMath.relativeBearing(0, 0, 90, -10, 0), 0.0001);
	}
}
