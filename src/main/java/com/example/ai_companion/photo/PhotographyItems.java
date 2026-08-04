package com.example.ai_companion.photo;

import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Registers the camera item and its server-authoritative right-click capture action. */
public final class PhotographyItems {
	private static final Identifier CAMERA_ID = Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "camera");
	private static final ResourceKey<Item> CAMERA_KEY = ResourceKey.create(Registries.ITEM, CAMERA_ID);
	public static final Item CAMERA = Registry.register(BuiltInRegistries.ITEM, CAMERA_KEY,
		new Item(new Item.Properties().setId(CAMERA_KEY).stacksTo(1)));

	private PhotographyItems() {}

	public static void register(PhotographyManager photography) {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(CAMERA));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!player.getItemInHand(hand).is(CAMERA)) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.SUCCESS;
			if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				try {
					PhotoEntry photo = photography.capture(serverPlayer);
					serverPlayer.sendSystemMessage(Component.literal("[相机] 已拍摄照片 #" + photo.id()
						+ "，并保存到相册"));
				} catch (RuntimeException | java.io.IOException error) {
					serverPlayer.sendSystemMessage(Component.literal("[相机] 拍摄失败：" + error.getMessage()));
				}
			}
			return InteractionResult.SUCCESS;
		});
	}
}
