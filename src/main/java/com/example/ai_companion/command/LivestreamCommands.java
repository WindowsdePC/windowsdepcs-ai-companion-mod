package com.example.ai_companion.command;

import com.example.ai_companion.livestream.LivestreamManager;
import com.example.ai_companion.livestream.LivestreamSession;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Player-facing commands for consensual AI livestream commentary. */
public final class LivestreamCommands {
	private LivestreamCommands() { }

	public static void register(LivestreamManager livestreams) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer").then(Commands.literal("live")
				.then(Commands.literal("status").executes(c -> status(c.getSource(), livestreams)))
				.then(Commands.literal("start")
					.then(Commands.argument("aiNames", StringArgumentType.word())
						.executes(c -> start(c.getSource(), livestreams,
							StringArgumentType.getString(c, "aiNames"), 30))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(10, 600))
							.executes(c -> start(c.getSource(), livestreams,
								StringArgumentType.getString(c, "aiNames"),
								IntegerArgumentType.getInteger(c, "seconds"))))))
				.then(Commands.literal("interval")
					.then(Commands.argument("seconds", IntegerArgumentType.integer(10, 600))
						.executes(c -> interval(c.getSource(), livestreams,
							IntegerArgumentType.getInteger(c, "seconds")))))
				.then(Commands.literal("stop").executes(c -> stop(c.getSource(), livestreams))))));
	}

	private static int start(CommandSourceStack source, LivestreamManager manager, String names,
			int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			List<String> viewers = Arrays.stream(names.split(",")).map(String::strip)
				.filter(value -> !value.isBlank()).toList();
			LivestreamSession session = manager.start(source.getPlayerOrException(), viewers, seconds * 20);
			source.sendSuccess(() -> Component.literal("AI 直播已开始：观众="
				+ String.join(", ", session.viewers()) + "，弹幕间隔=" + seconds + " 秒"), false);
			return 1;
		} catch (IOException | RuntimeException error) {
			source.sendFailure(Component.literal("开始 AI 直播失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int status(CommandSourceStack source, LivestreamManager manager)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			LivestreamSession session = manager.status(source.getPlayerOrException());
			source.sendSuccess(() -> Component.literal("AI 直播：观众=" + String.join(", ", session.viewers())
				+ "，间隔=" + session.intervalTicks() / 20 + " 秒，已生成="
				+ session.commentsGenerated() + " 条"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int interval(CommandSourceStack source, LivestreamManager manager, int seconds)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			manager.setInterval(source.getPlayerOrException(), seconds * 20);
			source.sendSuccess(() -> Component.literal("AI 直播弹幕间隔已设为 " + seconds + " 秒"), false);
			return 1;
		} catch (IOException | RuntimeException error) {
			source.sendFailure(Component.literal("修改直播失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int stop(CommandSourceStack source, LivestreamManager manager)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			boolean removed = manager.stop(source.getPlayerOrException());
			if (!removed) {
				source.sendFailure(Component.literal("你还没有 AI 直播会话"));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("AI 直播已停止"), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("停止直播失败：" + error.getMessage()));
			return 0;
		}
	}
}
