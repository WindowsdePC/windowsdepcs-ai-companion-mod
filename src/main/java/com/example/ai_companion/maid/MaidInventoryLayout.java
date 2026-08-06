package com.example.ai_companion.maid;

/** Slot layout shared by the standard 9x6 container UI and persistence tests. */
public final class MaidInventoryLayout {
	public static final int TOTAL_SLOTS = 54;
	public static final int STORAGE_SLOTS = 52;
	public static final int FIRST_BACKPACK_SLOT = 52;
	public static final int BACKPACK_SLOTS = 2;
	public static final int BASE_STORAGE_SLOTS = 18;
	public static final int STORAGE_SLOTS_PER_LEVEL = 2;
	public static final int STORAGE_SLOTS_PER_BACKPACK = 9;

	private MaidInventoryLayout() { }

	public static int baseUnlockedStorageSlots(int level) {
		return Math.min(STORAGE_SLOTS, BASE_STORAGE_SLOTS
			+ Math.clamp(level, 0, MaidProgression.MAX_LEVEL) * STORAGE_SLOTS_PER_LEVEL);
	}

	public static int unlockedStorageSlots(int level, int backpackCount) {
		return Math.min(STORAGE_SLOTS, baseUnlockedStorageSlots(level)
			+ Math.clamp(backpackCount, 0, BACKPACK_SLOTS) * STORAGE_SLOTS_PER_BACKPACK);
	}

	public static boolean isBackpackSlot(int slot) {
		return slot >= FIRST_BACKPACK_SLOT && slot < TOTAL_SLOTS;
	}
}
