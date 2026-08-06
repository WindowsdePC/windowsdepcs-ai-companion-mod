package com.example.ai_companion.legacy;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Minecraft 1.20.1 Fabric registration for natural-event items. */
final class LegacyWeatherItems {
	static final Item STAR_SHARD = Registry.register(BuiltInRegistries.ITEM,
		new ResourceLocation(LegacyFabricMod.MOD_ID, "star_shard"), new Item(new Item.Properties()));

	private LegacyWeatherItems() { }

	static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
			.register(entries -> entries.accept(STAR_SHARD));
	}
}
