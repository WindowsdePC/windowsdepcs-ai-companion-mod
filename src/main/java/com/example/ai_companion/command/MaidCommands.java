package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.ai_companion.maid.MaidManager;
import com.example.ai_companion.maid.MaidProfile;
import com.example.ai_companion.maid.MaidSkins;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Owner-safe command transport used by the maid UI. */
public final class MaidCommands {
	private MaidCommands() { }

	public static void register(MaidManager maids) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aimaid")
				.then(Commands.literal("summon")
					.then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("skin", StringArgumentType.word())
							.executes(c -> summon(c.getSource(), maids,
								StringArgumentType.getString(c, "name"),
								StringArgumentType.getString(c, "skin"), ""))
							.then(Commands.argument("cape", StringArgumentType.word())
								.executes(c -> summon(c.getSource(), maids,
									StringArgumentType.getString(c, "name"),
									StringArgumentType.getString(c, "skin"),
									StringArgumentType.getString(c, "cape")))))))
				.then(Commands.literal("chat")
					.then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("message", StringArgumentType.greedyString())
							.executes(c -> chat(c.getSource(), maids,
								StringArgumentType.getString(c, "name"),
								StringArgumentType.getString(c, "message"))))))
				.then(Commands.literal("mood")
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(c -> mood(c.getSource(), maids, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("list").executes(c -> list(c.getSource(), maids)))));
	}

	private static int summon(CommandSourceStack source, MaidManager maids, String name,
			String skin, String cape) {
		try {
			MaidProfile profile = maids.summon(source.getPlayerOrException(), name,
				MaidSkins.validate(skin), MaidSkins.validateOptional(cape));
			source.getPlayerOrException().sendOverlayMessage(Component.literal(
				"已召唤 AI 女仆 " + profile.name() + "，心情：" + profile.mood().displayName()));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("召唤女仆失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int chat(CommandSourceStack source, MaidManager maids, String name, String message) {
		try {
			var player = source.getPlayerOrException();
			maids.chat(player, name, message, result ->
				player.sendOverlayMessage(Component.literal(result)));
			player.sendOverlayMessage(Component.literal(name + " 正在思考……"));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("女仆对话失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int mood(CommandSourceStack source, MaidManager maids, String name) {
		try {
			MaidProfile profile = maids.profile(name);
			source.sendSuccess(() -> Component.literal(profile.name() + " 当前心情："
				+ profile.mood().displayName()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int list(CommandSourceStack source, MaidManager maids) {
		var profiles = maids.profiles();
		if (profiles.isEmpty()) {
			source.sendSuccess(() -> Component.literal("当前没有 AI 女仆"), false);
			return 0;
		}
		profiles.forEach(profile -> source.sendSuccess(() -> Component.literal("- " + profile.name()
			+ " · 主人=" + profile.ownerName() + " · 皮肤=" + profile.skinKey()
			+ " · 心情=" + profile.mood().displayName()), false));
		return profiles.size();
	}
}
