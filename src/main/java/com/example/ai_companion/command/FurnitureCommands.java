package com.example.ai_companion.command;

import com.example.ai_companion.furniture.FurnitureManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Commands for AI seating and furniture-area chat. */
public final class FurnitureCommands {
	private FurnitureCommands() { }

	public static void register(FurnitureManager furniture) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer").then(Commands.literal("furniture")
				.then(Commands.literal("sit").then(Commands.argument("ai", StringArgumentType.word())
					.executes(c -> sit(c.getSource(), furniture,
						StringArgumentType.getString(c, "ai")))))
				.then(Commands.literal("stand").then(Commands.argument("ai", StringArgumentType.word())
					.executes(c -> stand(c.getSource(), furniture,
						StringArgumentType.getString(c, "ai")))))
				.then(Commands.literal("chat").then(Commands.argument("ai", StringArgumentType.word())
					.then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(c -> chat(c.getSource(), furniture,
							StringArgumentType.getString(c, "ai"),
							StringArgumentType.getString(c, "message")))))))));
	}

	private static int sit(CommandSourceStack source, FurnitureManager furniture, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			var position = furniture.sitNearest(source.getPlayerOrException(), name);
			source.sendSuccess(() -> Component.literal(name + " 已坐到沙发上（" + position.toShortString()
				+ "）"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("AI 入座失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int stand(CommandSourceStack source, FurnitureManager furniture, String name) {
		try {
			furniture.stand(name);
			source.sendSuccess(() -> Component.literal(name + " 已从家具上起身"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("AI 起身失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int chat(CommandSourceStack source, FurnitureManager furniture, String name,
			String message) {
		try {
			furniture.chat(source.getServer(), name, message,
				result -> source.getServer().execute(() -> source.sendSuccess(
					() -> Component.literal(result), false)));
			source.sendSuccess(() -> Component.literal(name + " 正在家具区回应……"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("家具区聊天失败：" + error.getMessage()));
			return 0;
		}
	}
}
