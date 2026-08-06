package com.example.ai_companion.command;

import com.example.ai_companion.weather.ActiveWeatherEvent;
import com.example.ai_companion.weather.WeatherEventManager;
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

/** Commands for inspecting and controlling bounded natural events. */
public final class WeatherEventCommands {
	private WeatherEventCommands() { }

	public static void register(WeatherEventManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("aiplayer").then(Commands.literal("weather")
				.then(Commands.literal("status").executes(c -> status(c.getSource(), manager)))
				.then(Commands.literal("stop").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.executes(c -> stop(c.getSource(), manager)))
				.then(Commands.literal("start").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.argument("type", StringArgumentType.word())
						.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 30))
							.executes(c -> start(c.getSource(), manager, StringArgumentType.getString(c, "type"),
								IntegerArgumentType.getInteger(c, "minutes"))))))))));
	}

	private static int start(CommandSourceStack source, WeatherEventManager manager, String value, int minutes) {
		try {
			WeatherEventType type = WeatherEventType.parse(value);
			if (source.getLevel().dimension() != Level.OVERWORLD) throw new IllegalArgumentException("自然事件只能从主世界启动");
			long time = Math.floorMod(source.getLevel().getDayTime(), 24000L);
			if (type.nightOnly() && (time < 13000L || time > 23000L)) throw new IllegalArgumentException(type.displayName() + "只能在夜晚启动");
			if (type == WeatherEventType.SANDSTORM && !source.getLevel().getBiome(BlockPos.containing(source.getPosition())).is(Biomes.DESERT))
				throw new IllegalArgumentException("沙尘暴必须从沙漠群系启动");
			manager.start(type, minutes, false);
			source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[自然事件] " + type.displayName() + "开始，持续 " + minutes + " 分钟"), false);
			return 1;
		} catch (Exception error) { source.sendFailure(Component.literal("启动自然事件失败：" + error.getMessage())); return 0; }
	}

	private static int status(CommandSourceStack source, WeatherEventManager manager) {
		ActiveWeatherEvent event = manager.active();
		String message = event == null ? "当前没有自然事件" : event.type().displayName() + " · 剩余 " + event.remainingSeconds() + " 秒 · " + (event.automatic() ? "自然生成" : "管理员启动");
		source.sendSuccess(() -> Component.literal(message), false); return 1;
	}

	private static int stop(CommandSourceStack source, WeatherEventManager manager) {
		if (!manager.stop()) { source.sendFailure(Component.literal("当前没有自然事件")); return 0; }
		source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[自然事件] 已由管理员停止"), false); return 1;
	}
}
