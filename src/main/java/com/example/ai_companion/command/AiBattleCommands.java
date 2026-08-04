package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.arena.ArenaMode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.Arrays;
import java.util.List;

/** Registers the design-document-compatible /ai battle command tree. */
public final class AiBattleCommands {
	private AiBattleCommands() { }

	public static void register(AiArenaManager arena) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("ai")
				.then(Commands.literal("battle")
					.executes(context -> status(context.getSource(), arena))
					.then(Commands.literal("status")
						.executes(context -> status(context.getSource(), arena)))
					.then(Commands.literal("stop")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.executes(context -> stop(context.getSource(), arena)))
					.then(Commands.literal("1v1")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("first", StringArgumentType.word())
							.then(Commands.argument("second", StringArgumentType.word())
								.executes(context -> start(context.getSource(), arena, ArenaMode.ONE_V_ONE,
									List.of(StringArgumentType.getString(context, "first"),
										StringArgumentType.getString(context, "second")))))))
					.then(Commands.literal("2v2")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("team1a", StringArgumentType.word())
							.then(Commands.argument("team1b", StringArgumentType.word())
								.then(Commands.argument("team2a", StringArgumentType.word())
									.then(Commands.argument("team2b", StringArgumentType.word())
										.executes(context -> start(context.getSource(), arena, ArenaMode.TWO_V_TWO,
											List.of(StringArgumentType.getString(context, "team1a"),
												StringArgumentType.getString(context, "team1b"),
												StringArgumentType.getString(context, "team2a"),
												StringArgumentType.getString(context, "team2b"))))))))
					.then(Commands.literal("free-for-all")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("participants", StringArgumentType.greedyString())
							.executes(context -> start(context.getSource(), arena, ArenaMode.FREE_FOR_ALL,
								parseNames(StringArgumentType.getString(context, "participants")))))))));
	}

	private static List<String> parseNames(String input) {
		return Arrays.stream(input.trim().split("[\\s,]+"))
			.filter(name -> !name.isBlank()).toList();
	}

	private static int start(CommandSourceStack source, AiArenaManager arena, ArenaMode mode,
			List<String> names) {
		try {
			AiArenaManager.BattleView view = arena.start(source.getServer(), mode, names);
			source.sendSuccess(() -> Component.literal(view.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("无法开始 AI 竞技场：" + error.getMessage()));
			return 0;
		}
	}

	private static int status(CommandSourceStack source, AiArenaManager arena) {
		source.sendSuccess(() -> Component.literal(arena.view(source.getServer().getTickCount())
			.displayText()), false);
		return 1;
	}

	private static int stop(CommandSourceStack source, AiArenaManager arena) {
		if (!arena.stop(source.getServer(), "比赛已由管理员停止")) {
			source.sendFailure(Component.literal("当前没有进行中的 AI 竞技场比赛"));
			return 0;
		}
		return 1;
	}
}
