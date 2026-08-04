package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.ai_companion.orb.AssistantOrbManager;
import com.example.ai_companion.orb.AssistantReminder;
import com.example.ai_companion.orb.AssistantWaypoint;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Registers the player-facing /aiplayer orb command tree. */
public final class AssistantOrbCommands {
	private AssistantOrbCommands() {}

	public static void register(AssistantOrbManager orb) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var orbRoot = Commands.literal("orb")
				.then(Commands.literal("chat")
					.then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(c -> chat(c.getSource(), orb,
							StringArgumentType.getString(c, "message")))))
				.then(Commands.literal("explore").executes(c -> explore(c.getSource(), orb)))
				.then(Commands.literal("waypoint")
					.then(Commands.literal("save")
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(c -> saveWaypoint(c.getSource(), orb,
								StringArgumentType.getString(c, "name")))))
					.then(Commands.literal("list").executes(c -> listWaypoints(c.getSource(), orb)))
					.then(Commands.literal("remove")
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(c -> removeWaypoint(c.getSource(), orb,
								StringArgumentType.getString(c, "name"))))))
				.then(Commands.literal("remind")
					.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
						.then(Commands.argument("message", StringArgumentType.greedyString())
							.executes(c -> remind(c.getSource(), orb,
								IntegerArgumentType.getInteger(c, "minutes"),
								StringArgumentType.getString(c, "message"))))))
				.then(Commands.literal("reminders")
					.then(Commands.literal("list").executes(c -> listReminders(c.getSource(), orb)))
					.then(Commands.literal("cancel")
						.then(Commands.argument("id", LongArgumentType.longArg(1))
							.executes(c -> cancelReminder(c.getSource(), orb,
								LongArgumentType.getLong(c, "id"))))));
			dispatcher.register(Commands.literal("aiplayer").then(orbRoot));
		});
	}

	private static int chat(CommandSourceStack source, AssistantOrbManager orb, String message)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		orb.chat(source.getServer(), source.getPlayerOrException(), message);
		source.sendSuccess(() -> Component.literal("AI助手球正在思考……"), false);
		return 1;
	}

	private static int explore(CommandSourceStack source, AssistantOrbManager orb)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		source.sendSuccess(() -> Component.literal(orb.explorationSummary(source.getPlayerOrException())), false);
		return 1;
	}

	private static int saveWaypoint(CommandSourceStack source, AssistantOrbManager orb, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			AssistantWaypoint waypoint = orb.saveWaypoint(source.getPlayerOrException(), name);
			source.sendSuccess(() -> Component.literal("AI助手球已保存坐标：" + waypoint.displayText()), false);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal("保存坐标失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int listWaypoints(CommandSourceStack source, AssistantOrbManager orb)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		List<AssistantWaypoint> waypoints = orb.waypoints(source.getPlayerOrException());
		if (waypoints.isEmpty()) {
			source.sendSuccess(() -> Component.literal("AI助手球还没有保存坐标"), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal("AI助手球坐标（共 " + waypoints.size() + " 个）："), false);
		waypoints.forEach(waypoint -> source.sendSuccess(() -> Component.literal(waypoint.displayText()), false));
		return waypoints.size();
	}

	private static int removeWaypoint(CommandSourceStack source, AssistantOrbManager orb, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			if (!orb.removeWaypoint(source.getPlayerOrException(), name)) {
				source.sendFailure(Component.literal("找不到坐标：" + name));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("已删除坐标：" + name), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("删除坐标失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int remind(CommandSourceStack source, AssistantOrbManager orb, int minutes, String message)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			AssistantReminder reminder = orb.remind(source.getPlayerOrException(), minutes, message);
			source.sendSuccess(() -> Component.literal("已创建 AI助手球提醒 #" + reminder.id()
				+ "，将在 " + minutes + " 分钟后触发"), false);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal("创建提醒失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int listReminders(CommandSourceStack source, AssistantOrbManager orb)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		List<AssistantReminder> reminders = orb.reminders(source.getPlayerOrException());
		if (reminders.isEmpty()) {
			source.sendSuccess(() -> Component.literal("当前没有待触发提醒"), false);
			return 0;
		}
		long now = System.currentTimeMillis();
		for (AssistantReminder reminder : reminders) {
			long minutes = Math.max(0, Duration.ofMillis(reminder.dueAtEpochMillis() - now).toMinutes());
			source.sendSuccess(() -> Component.literal("#" + reminder.id() + " · 约 " + minutes
				+ " 分钟后 · " + reminder.message()), false);
		}
		return reminders.size();
	}

	private static int cancelReminder(CommandSourceStack source, AssistantOrbManager orb, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			if (!orb.cancelReminder(source.getPlayerOrException(), id)) {
				source.sendFailure(Component.literal("找不到提醒 #" + id));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("已取消提醒 #" + id), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("取消提醒失败：" + error.getMessage()));
			return 0;
		}
	}
}
