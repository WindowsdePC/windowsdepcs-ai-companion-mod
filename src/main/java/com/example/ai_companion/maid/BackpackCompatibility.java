package com.example.ai_companion.maid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Optional, reflection-free backpack detection; no external mod is a hard dependency. */
public final class BackpackCompatibility {
	private static final TagKey<Item> COMMON_BACKPACKS = TagKey.create(Registries.ITEM,
		Identifier.fromNamespaceAndPath("c", "backpacks"));
	private static final TagKey<Item> FABRIC_BACKPACKS = TagKey.create(Registries.ITEM,
		Identifier.fromNamespaceAndPath("fabric", "backpacks"));
	private static final Set<String> KNOWN_NAMESPACES = Set.of(
		"sophisticatedbackpacks", "travelersbackpack", "backpacked", "inmis", "packedup");

	private BackpackCompatibility() { }

	public static boolean isBackpack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (stack.is(COMMON_BACKPACKS) || stack.is(FABRIC_BACKPACKS)) return true;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath().toLowerCase();
		return KNOWN_NAMESPACES.contains(id.getNamespace()) || path.contains("backpack")
			|| path.endsWith("_satchel") || path.endsWith("_rucksack");
	}
}
