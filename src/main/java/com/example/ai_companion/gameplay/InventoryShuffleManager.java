package com.example.ai_companion.gameplay;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shuffles storage, armor and offhand while preserving the nine hotbar slots and every item. */
public final class InventoryShuffleManager {
	private static final int STORAGE_START = 9;
	private static final int STORAGE_END = 35;
	private static final int ARMOR_START = 36;
	private static final int ARMOR_END = 39;
	private static final int OFFHAND = Inventory.SLOT_OFFHAND;

	private InventoryShuffleManager() {
	}

	public static int shuffle(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> largeStacks = new ArrayList<>();
		List<ItemStack> singleStacks = new ArrayList<>();
		for (int slot : shuffledSlots()) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				(stack.getCount() > 1 ? largeStacks : singleStacks).add(stack.copy());
			}
		}

		Collections.shuffle(largeStacks);
		Collections.shuffle(singleStacks);
		List<Integer> unrestricted = unrestrictedSlots();
		Collections.shuffle(unrestricted);
		if (largeStacks.size() > unrestricted.size()) {
			throw new IllegalStateException("物品栏包含无效的装备栏堆叠，无法安全打乱");
		}
		for (int slot : shuffledSlots()) inventory.setItem(slot, ItemStack.EMPTY);

		int moved = largeStacks.size() + singleStacks.size();
		for (ItemStack stack : largeStacks) inventory.setItem(unrestricted.removeLast(), stack);
		List<Integer> remaining = new ArrayList<>(unrestricted);
		for (int slot = ARMOR_START; slot <= ARMOR_END; slot++) remaining.add(slot);
		Collections.shuffle(remaining);
		for (ItemStack stack : singleStacks) {
			if (remaining.isEmpty()) throw new IllegalStateException("打乱目标槽位不足");
			inventory.setItem(remaining.removeLast(), stack);
		}

		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
		return moved;
	}

	private static List<Integer> shuffledSlots() {
		List<Integer> slots = unrestrictedSlots();
		for (int slot = ARMOR_START; slot <= ARMOR_END; slot++) slots.add(slot);
		return slots;
	}

	private static List<Integer> unrestrictedSlots() {
		List<Integer> slots = new ArrayList<>();
		for (int slot = STORAGE_START; slot <= STORAGE_END; slot++) slots.add(slot);
		slots.add(OFFHAND);
		return slots;
	}
}
