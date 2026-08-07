package com.example.ai_companion.command;

import com.example.ai_companion.weather.ActiveWeatherEvent;
import com.example.ai_companion.weather.WeatherEventManager;
import com.example.ai_companion.weather.WeatherEventRecord;
import com.example.ai_companion.weather.WeatherEventSettings;
import com.example.ai_companion.weather.WeatherEventStatistics;
import com.example.ai_companion.weather.WeatherEventType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Commands for inspecting and controlling bounded natural events. */
public final class WeatherEventCommands {
	private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
	private WeatherEventCommands() { }

	public static void register(WeatherEventManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var admin = Permissions.COMMANDS_ADMIN;
			var weather = Commands.literal("weather")
				.then(Commands.literal("status").executes(c -> status(c.getSource(), manager)))
				.then(Commands.literal("forecast").executes(c -> forecast(c.getSource(), manager)))
				.then(Commands.literal("stats")
					.executes(c -> stats(c.getSource(), manager, null))
					.then(Commands.argument("type", StringArgumentType.word())
						.executes(c -> stats(c.getSource(), manager, StringArgumentType.getString(c, "type")))))
				.then(Commands.literal("history")
					.executes(c -> history(c.getSource(), manager, 5))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
						.executes(c -> history(c.getSource(), manager, IntegerArgumentType.getInteger(c, "count")))))
				.then(Commands.literal("notify").then(Commands.argument("enabled", StringArgumentType.word())
					.executes(c -> notify(c.getSource(), manager, StringArgumentType.getString(c, "enabled")))))
				.then(Commands.literal("config")
					.then(Commands.literal("status").executes(c -> configStatus(c.getSource(), manager)))
					.then(Commands.literal("enabled").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("value", StringArgumentType.word()).executes(c -> configEnabled(c.getSource(), manager, StringArgumentType.getString(c, "value")))))
					.then(Commands.literal("interval").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(30, 3600)).executes(c -> configInterval(c.getSource(), manager, IntegerArgumentType.getInteger(c, "seconds")))))
					.then(Commands.literal("chance").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("denominator", IntegerArgumentType.integer(1, 10000)).executes(c -> configChance(c.getSource(), manager, IntegerArgumentType.getInteger(c, "denominator")))))
					.then(Commands.literal("duration").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("minimum", IntegerArgumentType.integer(1, 30))
							.then(Commands.argument("maximum", IntegerArgumentType.integer(1, 30)).executes(c -> configDuration(c.getSource(), manager,
								IntegerArgumentType.getInteger(c, "minimum"), IntegerArgumentType.getInteger(c, "maximum"))))))
					.then(Commands.literal("cooldown").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440))
							.executes(c -> configCooldown(c.getSource(), manager, IntegerArgumentType.getInteger(c, "minutes")))))
					.then(Commands.literal("weight").requires(s -> s.permissions().hasPermission(admin))
						.then(Commands.argument("type", StringArgumentType.word())
							.then(Commands.argument("weight", IntegerArgumentType.integer(0, 1000))
								.executes(c -> configWeight(c.getSource(), manager, StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "weight")))))))
				.then(Commands.literal("stop").requires(s -> s.permissions().hasPermission(admin)).executes(c -> stop(c.getSource(), manager)))
				.then(Commands.literal("start").requires(s -> s.permissions().hasPermission(admin))
					.then(Commands.argument("type", StringArgumentType.word())
						.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 30))
							.executes(c -> start(c.getSource(), manager, StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "minutes"))))));
			dispatcher.register(Commands.literal("aiplayer").then(weather));
		});
	}

	private static int start(CommandSourceStack source, WeatherEventManager manager, String value, int minutes) {
		try {
			WeatherEventType type = WeatherEventType.parse(value);
			if (source.getLevel().dimension() != Level.OVERWORLD) throw new IllegalArgumentException("自然事件只能从主世界启动");
			long time = Math.floorMod(source.getLevel().getOverworldClockTime(), 24000L);
			if (type.nightOnly() && (time < 13000L || time > 23000L)) throw new IllegalArgumentException(type.displayName() + "只能在夜晚启动");
			if (type == WeatherEventType.SANDSTORM && !source.getLevel().getBiome(BlockPos.containing(source.getPosition())).is(Biomes.DESERT))
				throw new IllegalArgumentException("沙尘暴必须从沙漠群系启动");
			manager.start(type, minutes, false); manager.announce(source.getServer(), type.displayName() + "开始，持续 " + minutes + " 分钟"); return 1;
		} catch (Exception error) { source.sendFailure(Component.literal("启动自然事件失败：" + error.getMessage())); return 0; }
	}

	private static int status(CommandSourceStack source, WeatherEventManager manager) {
		ActiveWeatherEvent event = manager.active();
		String message = event == null ? "当前没有自然事件" : event.type().displayName() + " · 剩余 " + event.remainingSeconds() + " 秒 · " + (event.automatic() ? "自然生成" : "管理员启动");
		source.sendSuccess(() -> Component.literal(message), false); return 1;
	}

	private static int forecast(CommandSourceStack source, WeatherEventManager manager) {
		WeatherEventSettings settings = manager.settings();
		int cooldown = manager.automaticCooldownRemainingSeconds();
		long time = Math.floorMod(source.getLevel().getOverworldClockTime(), 24000L);
		String eligible = manager.eligibleTypeLabels(time >= 13000L && time <= 23000L);
		String detail = cooldown > 0 ? "冷却剩余 " + cooldown + " 秒" : manager.nextAutomaticCheckSeconds() + " 秒后检查，候选 " + eligible;
		source.sendSuccess(() -> Component.literal("自然事件预报：" + (settings.automaticEnabled() ? detail : "自动生成已关闭")), false); return 1;
	}

	private static int history(CommandSourceStack source, WeatherEventManager manager, int count) {
		var entries = manager.history(count);
		if (entries.isEmpty()) { source.sendSuccess(() -> Component.literal("尚无自然事件历史"), false); return 1; }
		for (WeatherEventRecord entry : entries) source.sendSuccess(() -> Component.literal(HISTORY_TIME.format(Instant.ofEpochMilli(entry.startedAtEpochMillis()))
			+ " · " + entry.type().displayName() + " · " + entry.plannedDurationSeconds() + " 秒 · " + (entry.automatic() ? "自然" : "管理员")), false);
		return entries.size();
	}

	private static int stats(CommandSourceStack source, WeatherEventManager manager, String rawType) {
		try {
			WeatherEventType type = rawType == null ? null : WeatherEventType.parse(rawType);
			var records = manager.history(32).stream().filter(record -> type == null || record.type() == type).toList();
			WeatherEventStatistics value = WeatherEventStatistics.from(records);
			String label = type == null ? "全部事件" : type.displayName();
			source.sendSuccess(() -> Component.literal(label + "统计：共 " + value.events() + " 次（自然 "
				+ value.automaticEvents() + " / 管理员 " + value.administratorEvents() + "），计划总时长 "
				+ value.plannedDurationSeconds() + " 秒"), false);
			return value.events();
		} catch (Exception error) {
			source.sendFailure(Component.literal("读取自然事件统计失败：" + error.getMessage())); return 0;
		}
	}

	private static int notify(CommandSourceStack source, WeatherEventManager manager, String raw) {
		try {
			boolean enabled = parseBoolean(raw); manager.setNotifications(source.getPlayerOrException().getUUID(), enabled);
			source.sendSuccess(() -> Component.literal("自然事件通知已" + (enabled ? "开启" : "关闭")), false); return 1;
		} catch (Exception error) { source.sendFailure(Component.literal("通知设置失败：" + error.getMessage())); return 0; }
	}

	private static int configStatus(CommandSourceStack source, WeatherEventManager manager) {
		WeatherEventSettings value = manager.settings();
		source.sendSuccess(() -> Component.literal("自动=" + value.automaticEnabled() + "，间隔=" + value.checkIntervalSeconds()
			+ "秒，单次概率=1/" + value.chanceDenominator() + "，时长=" + value.minDurationMinutes() + "～" + value.maxDurationMinutes()
			+ "分钟，自动冷却=" + manager.automaticCooldownMinutes() + "分钟，权重：" + manager.typeWeightSummary()), false); return 1;
	}
	private static int configEnabled(CommandSourceStack source, WeatherEventManager manager, String raw) { try { manager.updateSettings(manager.settings().withEnabled(parseBoolean(raw))); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configInterval(CommandSourceStack source, WeatherEventManager manager, int value) { try { manager.updateSettings(manager.settings().withInterval(value)); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configChance(CommandSourceStack source, WeatherEventManager manager, int value) { try { manager.updateSettings(manager.settings().withChance(value)); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configDuration(CommandSourceStack source, WeatherEventManager manager, int min, int max) { try { manager.updateSettings(manager.settings().withDuration(min, max)); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configCooldown(CommandSourceStack source, WeatherEventManager manager, int minutes) { try { manager.setAutomaticCooldownMinutes(minutes); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configWeight(CommandSourceStack source, WeatherEventManager manager, String rawType, int weight) { try { manager.setTypeWeight(WeatherEventType.parse(rawType), weight); return configStatus(source, manager); } catch (Exception error) { return configFailure(source, error); } }
	private static int configFailure(CommandSourceStack source, Exception error) { source.sendFailure(Component.literal("自然事件配置失败：" + error.getMessage())); return 0; }
	private static boolean parseBoolean(String raw) {
		return switch (raw.toLowerCase(java.util.Locale.ROOT)) { case "true", "on", "yes", "1" -> true; case "false", "off", "no", "0" -> false; default -> throw new IllegalArgumentException("值必须是 on/off 或 true/false"); };
	}

	private static int stop(CommandSourceStack source, WeatherEventManager manager) {
		if (!manager.stop()) { source.sendFailure(Component.literal("当前没有自然事件")); return 0; }
		manager.announce(source.getServer(), "已由管理员停止"); return 1;
	}
}
