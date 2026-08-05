package com.example.ai_companion.command;

import com.example.ai_companion.news.DailyNewsIssue;
import com.example.ai_companion.news.MinecraftDailyNewsManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.List;

/** Registers the player-facing /aiplayer news Minecraft Daily command tree. */
public final class MinecraftDailyNewsCommands {
	private static final int PAGE_SIZE = 6;

	private MinecraftDailyNewsCommands() { }

	public static void register(MinecraftDailyNewsManager news) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var list = Commands.literal("list")
				.executes(context -> list(context.getSource(), news, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(context -> list(context.getSource(), news,
						IntegerArgumentType.getInteger(context, "page"))));
			var root = Commands.literal("news")
				.then(list)
				.then(Commands.literal("today")
					.executes(context -> generate(context.getSource(), news)))
				.then(Commands.literal("generate")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.executes(context -> generate(context.getSource(), news)))
				.then(Commands.literal("show")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(context -> show(context.getSource(), news,
							LongArgumentType.getLong(context, "id")))))
				.then(Commands.literal("ai")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(context -> aiEdit(context.getSource(), news,
							LongArgumentType.getLong(context, "id")))));
			dispatcher.register(Commands.literal("aiplayer").then(root));
		});
	}

	private static int list(CommandSourceStack source, MinecraftDailyNewsManager news, int page) {
		List<DailyNewsIssue> issues = news.issues();
		if (issues.isEmpty()) {
			source.sendSuccess(() -> Component.literal("尚无 Minecraft 日报；使用 /aiplayer news today 生成今日版"), false);
			return 0;
		}
		int pages = Math.max(1, (issues.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		if (page > pages) {
			source.sendFailure(Component.literal("日报存档只有 " + pages + " 页"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Minecraft 日报第 " + page + "/" + pages
			+ " 页（共 " + issues.size() + " 期）："), false);
		issues.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE)
			.forEach(issue -> source.sendSuccess(() -> Component.literal(issue.summaryText()), false));
		return 1;
	}

	private static int generate(CommandSourceStack source, MinecraftDailyNewsManager news) {
		try {
			DailyNewsIssue issue = news.generateCurrent(source.getServer());
			source.sendSuccess(() -> Component.literal("已生成 " + issue.summaryText()
				+ "；使用 /aiplayer news show " + issue.id() + " 阅读"), false);
			return 1;
		} catch (IOException | RuntimeException error) {
			source.sendFailure(Component.literal("生成日报失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int show(CommandSourceStack source, MinecraftDailyNewsManager news, long id) {
		try {
			DailyNewsIssue issue = news.requireIssue(id);
			source.sendSuccess(() -> Component.literal(issue.displayText()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int aiEdit(CommandSourceStack source, MinecraftDailyNewsManager news, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			news.requestAiEdition(source.getServer(), source.getPlayerOrException(), id);
			source.sendSuccess(() -> Component.literal("AI 正在编辑 Minecraft 日报 #" + id + "……"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("请求 AI 编辑失败：" + error.getMessage()));
			return 0;
		}
	}
}
