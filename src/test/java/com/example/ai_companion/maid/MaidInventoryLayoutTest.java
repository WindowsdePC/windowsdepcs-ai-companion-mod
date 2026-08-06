package com.example.ai_companion.maid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaidInventoryLayoutTest {
	@Test void startsWithExactlyTwoRows() {
		assertEquals(18, MaidInventoryLayout.baseUnlockedStorageSlots(0));
		assertEquals(20, MaidInventoryLayout.baseUnlockedStorageSlots(1));
	}

	@Test void backpacksExpandWithoutExceedingStorageRegion() {
		assertEquals(27, MaidInventoryLayout.unlockedStorageSlots(0, 1));
		assertEquals(36, MaidInventoryLayout.unlockedStorageSlots(0, 2));
		assertEquals(52, MaidInventoryLayout.unlockedStorageSlots(30, 2));
	}

	@Test void exactlyTwoSlotsAreReservedForBackpacks() {
		assertFalse(MaidInventoryLayout.isBackpackSlot(51));
		assertTrue(MaidInventoryLayout.isBackpackSlot(52));
		assertTrue(MaidInventoryLayout.isBackpackSlot(53));
		assertFalse(MaidInventoryLayout.isBackpackSlot(54));
	}
}
