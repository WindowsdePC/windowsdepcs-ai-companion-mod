package com.example.ai_companion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AdaptiveRenderDistanceTest {
	@Test
	void reducesDistanceBelowLowerHysteresisBand() {
		assertEquals(88, AdaptiveRenderDistance.next(96, 24, 128, 60, 50.0));
	}

	@Test
	void holdsDistanceInsideHysteresisBand() {
		assertEquals(96, AdaptiveRenderDistance.next(96, 24, 128, 60, 63.0));
	}

	@Test
	void restoresDistanceOnlyAboveUpperBand() {
		assertEquals(104, AdaptiveRenderDistance.next(96, 24, 128, 60, 75.0));
	}

	@Test
	void respectsConfiguredBounds() {
		assertEquals(24, AdaptiveRenderDistance.next(24, 24, 96, 60, 10.0));
		assertEquals(96, AdaptiveRenderDistance.next(96, 24, 96, 60, 200.0));
	}
}
