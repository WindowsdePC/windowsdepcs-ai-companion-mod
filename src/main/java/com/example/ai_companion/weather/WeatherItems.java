package com.example.ai_companion.weather;

import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Items produced by the server-authoritative natural-event system. */
public final class WeatherItems {
	private static final Identifier STAR_SHARD_ID =
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "star_shard");
	private static final ResourceKey<Item> STAR_SHARD_KEY =
		ResourceKey.create(Registries.ITEM, STAR_SHARD_ID);

	public static final Item STAR_SHARD = Registry.register(BuiltInRegistries.ITEM, STAR_SHARD_KEY,
		new Item(new Item.Properties().setId(STAR_SHARD_KEY)));

	private WeatherItems() { }

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
			.register(output -> output.accept(STAR_SHARD));
	}
}
