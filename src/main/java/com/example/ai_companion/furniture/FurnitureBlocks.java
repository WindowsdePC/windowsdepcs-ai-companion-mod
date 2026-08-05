package com.example.ai_companion.furniture;

import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers the four placeable furniture blocks and their inventory items. */
public final class FurnitureBlocks {
	public static final Block SOFA = register(FurnitureCatalog.SOFA,
		BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.WOOL));
	public static final Block TELEVISION = register(FurnitureCatalog.TELEVISION,
		BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL));
	public static final Block COMPUTER = register(FurnitureCatalog.COMPUTER,
		BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL));
	public static final Block TABLE_LAMP = register(FurnitureCatalog.TABLE_LAMP,
		BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.LANTERN)
			.lightLevel(state -> FurnitureCatalog.TABLE_LAMP.lightLevel()));

	private FurnitureBlocks() { }

	private static Block register(FurnitureCatalog.Definition definition,
			BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, definition.id());
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Block block = new Block(properties.setId(blockKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(SOFA);
			output.accept(TELEVISION);
			output.accept(COMPUTER);
			output.accept(TABLE_LAMP);
		});
	}
}
