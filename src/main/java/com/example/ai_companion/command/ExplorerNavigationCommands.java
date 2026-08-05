package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.exploration.ExplorerNavigationManager;
import com.example.ai_companion.exploration.NavigationMode;
import com.example.ai_companion.exploration.NavigationSnapshot;
import com.example.ai_companion.exploration.NavigationTargetType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Commands used by the explorer compass UI and by dedicated-server administrators. */
public final class ExplorerNavigationCommands {
	private ExplorerNavigationCommands() { }

	public static void register(ExplorerNavigationManager navigation,
			Supplier<GameplayConfig> config, Consumer<GameplayConfig> update) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("navigator")
				.then(Commands.literal("mode")
					.then(Commands.literal("navigate").executes(c -> mode(c.getSource(), navigation,
						NavigationMode.NAVIGATE)))
					.then(Commands.literal("teleport").executes(c -> mode(c.getSource(), navigation,
						NavigationMode.TELEPORT))))
				.then(Commands.literal("target")
					.then(Commands.literal("biome")
						.then(Commands.argument("id", StringArgumentType.word()).executes(c -> target(
							c.getSource(), navigation, NavigationTargetType.BIOME,
							StringArgumentType.getString(c, "id")))))
					.then(Commands.literal("structure")
						.then(Commands.argument("id", StringArgumentType.word()).executes(c -> target(
							c.getSource(), navigation, NavigationTargetType.STRUCTURE,
							StringArgumentType.getString(c, "id")))))
					.then(Commands.literal("borderlands").executes(c -> target(c.getSource(), navigation,
						NavigationTargetType.BORDERLANDS, "ai_companion:borderlands"))))
				.then(Commands.literal("start").executes(c -> start(c.getSource(), navigation)))
				.then(Commands.literal("stop").executes(c -> stop(c.getSource(), navigation)))
				.then(Commands.literal("status").executes(c -> status(c.getSource(), navigation)));
			LiteralArgumentBuilder<CommandSourceStack> feature = Commands.literal("feature")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("navigation")
						.then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> save(
							c.getSource(), config.get().withExplorerNavigatorEnabled(
								BoolArgumentType.getBool(c, "value")), update))))
					.then(Commands.literal("world-limits")
						.then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> save(
							c.getSource(), config.get().withWorldLimitsRemoved(
								BoolArgumentType.getBool(c, "value")), update))))
					.then(Commands.literal("merciful-void")
						.then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> save(
							c.getSource(), config.get().withMercifulVoidEnabled(
								BoolArgumentType.getBool(c, "value")), update))));
			root.then(feature);
			dispatcher.register(root);
		});
	}

	private static int mode(CommandSourceStack source, ExplorerNavigationManager navigation,
			NavigationMode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		navigation.setMode(source.getPlayerOrException(), mode);
		source.sendSuccess(() -> Component.literal("指南针模式已改为：" + mode.label()), false);
		return 1;
	}

	private static int target(CommandSourceStack source, ExplorerNavigationManager navigation,
			NavigationTargetType type, String id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			navigation.setTarget(source.getPlayerOrException(), type, id);
			source.sendSuccess(() -> Component.literal("指南针目标已改为：" + type.label() + " " + id), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int start(CommandSourceStack source, ExplorerNavigationManager navigation)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			NavigationSnapshot result = navigation.start(source.getPlayerOrException());
			source.sendSuccess(() -> Component.literal(result.mode() == NavigationMode.TELEPORT
				? "已传送到 " + result.targetId() : "已开始 AR 导航到 " + result.targetId()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int stop(CommandSourceStack source, ExplorerNavigationManager navigation)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		navigation.stop(source.getPlayerOrException());
		source.sendSuccess(() -> Component.literal("已停止结构群系导航"), false);
		return 1;
	}

	private static int status(CommandSourceStack source, ExplorerNavigationManager navigation)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String message = navigation.status(source.getPlayerOrException());
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int save(CommandSourceStack source, GameplayConfig updated,
			Consumer<GameplayConfig> update) {
		try {
			updated.save();
			update.accept(updated);
			source.sendSuccess(() -> Component.literal("导航与世界安全设置已保存"), true);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("配置保存失败: " + error.getMessage()));
			return 0;
		}
	}
}
