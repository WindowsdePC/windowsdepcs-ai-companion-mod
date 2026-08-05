package com.example.ai_companion.world;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Operator commands backing the high-risk section of the unified UI. */
public final class WorldFeatureCommands {
	private WorldFeatureCommands() {
	}

	public static void register(Supplier<WorldFeatureConfig> config,
			Consumer<WorldFeatureConfig> update) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer-world")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("status").executes(context -> status(context.getSource(), config.get())))
				.then(toggle("navigator", value -> config.get().withNavigatorEnabled(value), update))
				.then(toggle("merciful-void", value -> config.get().withMercifulVoidEnabled(value), update))
				.then(toggle("maximum-border", value -> config.get().withMaximumWorldBorderEnabled(value), update))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> toggle(
			String name, java.util.function.Function<Boolean, WorldFeatureConfig> change,
			Consumer<WorldFeatureConfig> update) {
		return Commands.literal(name).then(Commands.argument("value", BoolArgumentType.bool())
			.executes(context -> save(context.getSource(),
				change.apply(BoolArgumentType.getBool(context, "value")), update)));
	}

	private static int status(CommandSourceStack source, WorldFeatureConfig config) {
		source.sendSuccess(() -> Component.literal("导航=" + config.navigatorEnabled()
			+ "，仁慈虚空=" + config.mercifulVoidEnabled()
			+ "，原版最大世界边界=" + config.maximumWorldBorderEnabled()), false);
		return 1;
	}

	private static int save(CommandSourceStack source, WorldFeatureConfig value,
			Consumer<WorldFeatureConfig> update) {
		try {
			value.save();
			update.accept(value);
			source.sendSuccess(() -> Component.literal("世界增强设置已保存；高危选项会立即生效"), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("保存世界增强设置失败：" + error.getMessage()));
			return 0;
		}
	}
}
