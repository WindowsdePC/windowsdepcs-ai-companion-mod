package com.example.ai_companion.exploration;

import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Registers the explorer compass and starts the player's selected target on right-click. */
public final class ExplorerNavigationItems {
	private static final Identifier COMPASS_ID = Identifier.fromNamespaceAndPath(
		AiCompanionMod.MOD_ID, "explorer_compass");
	private static final ResourceKey<Item> COMPASS_KEY = ResourceKey.create(Registries.ITEM, COMPASS_ID);
	public static final Item EXPLORER_COMPASS = Registry.register(BuiltInRegistries.ITEM, COMPASS_KEY,
		new Item(new Item.Properties().setId(COMPASS_KEY).stacksTo(1)));

	private ExplorerNavigationItems() { }

	public static void register(ExplorerNavigationManager navigation) {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(EXPLORER_COMPASS));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!player.getItemInHand(hand).is(EXPLORER_COMPASS)) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.SUCCESS;
			if (player instanceof ServerPlayer serverPlayer) {
				try {
					NavigationSnapshot result = navigation.start(serverPlayer);
					serverPlayer.sendSystemMessage(Component.literal(result.mode() == NavigationMode.TELEPORT
						? "[结构群系指南针] 已传送到 " + result.targetId()
						: "[结构群系指南针] 已开始导航：" + result.targetId()));
				} catch (RuntimeException error) {
					serverPlayer.sendSystemMessage(Component.literal("[结构群系指南针] " + error.getMessage()));
				}
			}
			return InteractionResult.SUCCESS;
		});
	}
}
