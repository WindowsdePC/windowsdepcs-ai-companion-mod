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
								StringArgumentType.getString(c, "message"), false)))))
				.then(Commands.literal("voice")
					.then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("transcript", StringArgumentType.greedyString())
							.executes(c -> chat(c.getSource(), maids,
								StringArgumentType.getString(c, "name"),
								StringArgumentType.getString(c, "transcript"), true)))))
				.then(Commands.literal("collect")
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(c -> collect(c.getSource(), maids, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("deploy")
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(c -> deploy(c.getSource(), maids, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("transfer")
					.then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("player", StringArgumentType.word())
							.executes(c -> transfer(c.getSource(), maids,
								StringArgumentType.getString(c, "name"),
								StringArgumentType.getString(c, "player"))))))
				.then(Commands.literal("voice-status").executes(c -> voiceStatus(c.getSource(), maids)))
				.then(Commands.literal("progress")
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(c -> progress(c.getSource(), maids, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("upgrade")
					.then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.literal("work").executes(c -> upgrade(c.getSource(), maids,
							StringArgumentType.getString(c, "name"), false)))
						.then(Commands.literal("player").executes(c -> upgrade(c.getSource(), maids,
							StringArgumentType.getString(c, "name"), true)))))
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

	private static int chat(CommandSourceStack source, MaidManager maids, String name, String message,
			boolean voiceTranscript) {
		try {
			var player = source.getPlayerOrException();
			String instruction = voiceTranscript ? "以下内容来自主人语音转写：" + message : message;
			maids.chat(player, name, instruction, result ->
				player.sendOverlayMessage(Component.literal(result)));
			player.sendOverlayMessage(Component.literal(name + " 正在思考……"));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("女仆对话失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int collect(CommandSourceStack source, MaidManager maids, String name) {
		try {
			maids.collect(source.getPlayerOrException(), name);
			source.getPlayerOrException().sendOverlayMessage(Component.literal(name + " 已收回背包"));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("收回失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int deploy(CommandSourceStack source, MaidManager maids, String name) {
		try {
			maids.deploy(source.getPlayerOrException(), name);
			source.getPlayerOrException().sendOverlayMessage(Component.literal(name + " 已从背包重新召唤"));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("重新召唤失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int transfer(CommandSourceStack source, MaidManager maids, String name,
			String targetName) {
		try {
			var target = source.getServer().getPlayerList().getPlayerByName(targetName);
			if (target == null) throw new IllegalStateException("目标玩家不在线");
			maids.transfer(source.getPlayerOrException(), name, target);
			source.sendSuccess(() -> Component.literal(name + " 的所有权已转让给 " + targetName), false);
			target.sendSystemMessage(Component.literal("你现在是 AI 女仆 " + name + " 的所有者"));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("转让失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int voiceStatus(CommandSourceStack source, MaidManager maids) {
		boolean available = maids.voiceChatAvailable();
		source.sendSuccess(() -> Component.literal(available
			? "已检测到 Simple Voice Chat；可把语音转写发送到 /aimaid voice"
			: "未检测到 Simple Voice Chat；文字聊天不受影响"), false);
		return available ? 1 : 0;
	}

	private static int progress(CommandSourceStack source, MaidManager maids, String name) {
		try {
			source.sendSuccess(() -> Component.literal(maids.progressionStatus(name)), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("读取成长状态失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int upgrade(CommandSourceStack source, MaidManager maids, String name,
			boolean usePlayerExperience) {
		try {
			MaidProfile profile = usePlayerExperience
				? maids.upgradeWithPlayerExperience(source.getPlayerOrException(), name)
				: maids.upgradeWithWorkExperience(source.getPlayerOrException(), name);
			source.getPlayerOrException().sendOverlayMessage(Component.literal(
				profile.name() + " 已升级到 Lv." + profile.level() + "，最大生命 "
					+ (int) com.example.ai_companion.maid.MaidProgression.maxHealth(profile.level())));
			return 1;
		} catch (Exception error) {
			source.sendFailure(Component.literal("升级失败：" + error.getMessage()));
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
			+ " · Lv." + profile.level() + " · 工作经验=" + profile.workExperience()
			+ " · 心情=" + profile.mood().displayName()), false));
		return profiles.size();
	}
}
