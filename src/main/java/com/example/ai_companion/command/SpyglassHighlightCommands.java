package com.example.ai_companion.command;

import com.example.ai_companion.spyglass.SpyglassHighlightManager;
import com.example.ai_companion.spyglass.SpyglassHighlightSettings;
import com.example.ai_companion.spyglass.SpyglassTargetCondition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Self-service commands used by both in-game UI backends. */
public final class SpyglassHighlightCommands {
	private SpyglassHighlightCommands() {}

	public static void register(SpyglassHighlightManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var spyglass = Commands.literal("spyglass");
			spyglass.then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException(), manager)));
			spyglass.then(Commands.literal("enabled").then(Commands.argument("value", BoolArgumentType.bool()).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withEnabled(BoolArgumentType.getBool(c, "value"))))));
			spyglass.then(Commands.literal("radius-chunks").then(Commands.argument("value", IntegerArgumentType.integer(1, 32)).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withRadiusChunks(IntegerArgumentType.getInteger(c, "value"))))));
			spyglass.then(Commands.literal("hold-seconds").then(Commands.argument("value", IntegerArgumentType.integer(1, 10)).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withHoldSeconds(IntegerArgumentType.getInteger(c, "value"))))));
			spyglass.then(Commands.literal("duration-seconds").then(Commands.argument("value", IntegerArgumentType.integer(1, 600)).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withEffectSeconds(IntegerArgumentType.getInteger(c, "value"))))));
			spyglass.then(Commands.literal("target").then(Commands.argument("value", StringArgumentType.word()).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withTargetCondition(SpyglassTargetCondition.parse(StringArgumentType.getString(c, "value")))))));
			spyglass.then(Commands.literal("cooldown-seconds").then(Commands.argument("value", IntegerArgumentType.integer(1, 600)).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withCooldownSeconds(IntegerArgumentType.getInteger(c, "value"))))));
			spyglass.then(Commands.literal("max-targets").then(Commands.argument("value", IntegerArgumentType.integer(1, 1024)).executes(c ->
				update(c.getSource().getPlayerOrException(), manager, manager.settings(c.getSource().getPlayerOrException().getUUID())
					.withMaxTargets(IntegerArgumentType.getInteger(c, "value"))))));
			dispatcher.register(Commands.literal("aiplayer").then(spyglass));
		});
	}

	private static int update(ServerPlayer player, SpyglassHighlightManager manager, SpyglassHighlightSettings settings) {
		manager.update(player.getUUID(), settings);
		return status(player, manager);
	}

	private static int status(ServerPlayer player, SpyglassHighlightManager manager) {
		SpyglassHighlightSettings value = manager.settings(player.getUUID());
		player.sendSystemMessage(Component.literal("望远镜发光=" + value.enabled() + "，半径=" + value.radiusChunks()
			+ "区块，观察=" + value.holdTicks() / 20 + "秒，持续=" + value.effectTicks() / 20
			+ "秒，目标=" + value.targetCondition().name().toLowerCase(java.util.Locale.ROOT)
			+ "，冷却=" + value.cooldownTicks() / 20 + "秒，单次上限=" + value.maxTargets()));
		return 1;
	}
}
