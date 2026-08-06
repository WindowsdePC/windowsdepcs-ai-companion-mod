package com.example.ai_companion.maid;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/** A standard chest-compatible container with level gates and two backpack mounts. */
final class MaidInventoryContainer extends SimpleContainer {
	private final UUID ownerUuid;
	private final int maidLevel;
	private boolean loading;
	private Runnable changeListener = () -> { };

	MaidInventoryContainer(UUID ownerUuid, int maidLevel, List<ItemStack> stacks) {
		super(MaidInventoryLayout.TOTAL_SLOTS);
		this.ownerUuid = ownerUuid;
		this.maidLevel = maidLevel;
		loading = true;
		for (int slot = 0; slot < Math.min(stacks.size(), getContainerSize()); slot++) {
			super.setItem(slot, stacks.get(slot).copy());
		}
		loading = false;
	}

	@Override public boolean canPlaceItem(int slot, ItemStack stack) {
		if (MaidInventoryLayout.isBackpackSlot(slot)) return BackpackCompatibility.isBackpack(stack);
		return slot >= 0 && slot < unlockedStorageSlots();
	}

	@Override public boolean stillValid(Player player) { return player.getUUID().equals(ownerUuid); }

	@Override public void setChanged() {
		if (loading) return;
		super.setChanged();
		changeListener.run();
	}

	void setChangeListener(Runnable listener) {
		changeListener = listener == null ? () -> { } : listener;
	}

	int unlockedStorageSlots() {
		int backpacks = 0;
		for (int slot = MaidInventoryLayout.FIRST_BACKPACK_SLOT;
				slot < MaidInventoryLayout.TOTAL_SLOTS; slot++) {
			if (BackpackCompatibility.isBackpack(getItem(slot))) backpacks++;
		}
		return MaidInventoryLayout.unlockedStorageSlots(maidLevel, backpacks);
	}

	List<ItemStack> snapshot() {
		List<ItemStack> result = new ArrayList<>(getContainerSize());
		for (int slot = 0; slot < getContainerSize(); slot++) result.add(getItem(slot).copy());
		return result;
	}
}
