package com.example.ai_companion.legacy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Minecraft 1.20.1 Forge registration for natural-event items. */
final class LegacyWeatherItems {
	private static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, LegacyForgeMod.MOD_ID);
	static final RegistryObject<Item> STAR_SHARD = ITEMS.register("star_shard",
		() -> new Item(new Item.Properties()));

	private LegacyWeatherItems() { }

	static void register() {
		var eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ITEMS.register(eventBus);
		eventBus.addListener(LegacyWeatherItems::addCreativeTabEntries);
	}

	private static void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(STAR_SHARD);
	}
}
