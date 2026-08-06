package com.example.ai_companion.command;

import com.example.ai_companion.pet.PetAttribute;
import com.example.ai_companion.pet.PetCompetitionManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Player-owned AI pet competition commands. */
public final class PetCompetitionCommands {
	private PetCompetitionCommands() {}

	public static void register(PetCompetitionManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aipet")
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> run(c.getSource(), manager, "create",
						StringArgumentType.getString(c, "name"), ""))))
				.then(Commands.literal("list").executes(c -> list(c.getSource(), manager)))
				.then(Commands.literal("status").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> run(c.getSource(), manager, "status",
						StringArgumentType.getString(c, "name"), ""))))
				.then(Commands.literal("train").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("attribute", StringArgumentType.word())
						.executes(c -> run(c.getSource(), manager, "train",
							StringArgumentType.getString(c, "name"),
							StringArgumentType.getString(c, "attribute"))))))
				.then(Commands.literal("race").then(Commands.argument("pets", StringArgumentType.greedyString())
					.executes(c -> run(c.getSource(), manager, "race",
						StringArgumentType.getString(c, "pets"), ""))))
				.then(Commands.literal("battle").then(Commands.argument("first", StringArgumentType.word())
					.then(Commands.argument("second", StringArgumentType.word())
						.executes(c -> run(c.getSource(), manager, "battle",
							StringArgumentType.getString(c, "first"),
							StringArgumentType.getString(c, "second"))))))));
	}

	private static int list(CommandSourceStack source, PetCompetitionManager manager) {
		try {
			var pets = manager.list(source.getPlayerOrException().getUUID());
			if (pets.isEmpty()) { source.sendSuccess(() -> Component.literal("你还没有竞技宠物"), false); return 0; }
			pets.forEach(pet -> source.sendSuccess(() -> Component.literal(pet.displayText()), false));
			return pets.size();
		} catch (Exception error) { return fail(source, error); }
	}

	private static int run(CommandSourceStack source, PetCompetitionManager manager,
			String action, String first, String second) {
		try {
			var owner = source.getPlayerOrException().getUUID();
			String message = switch (action) {
				case "create" -> "已创建宠物：" + manager.create(owner, first).displayText();
				case "status" -> manager.status(owner, first).displayText();
				case "train" -> "训练完成：" + manager.train(owner, first, PetAttribute.parse(second),
					System.currentTimeMillis()).displayText();
				case "race" -> {
					var result = manager.race(owner, first, source.getServer().getTickCount());
					yield "竞速冠军：" + result.winner() + "；排名：" + String.join(" > ", result.ranking());
				}
				case "battle" -> {
					var result = manager.battle(owner, first, second, source.getServer().getTickCount());
					yield "战斗胜者：" + result.winner() + "（" + result.rounds() + " 回合）";
				}
				default -> throw new IllegalArgumentException("未知操作");
			};
			source.sendSuccess(() -> Component.literal(message), false);
			return 1;
		} catch (Exception error) { return fail(source, error); }
	}

	private static int fail(CommandSourceStack source, Exception error) {
		source.sendFailure(Component.literal(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
		return 0;
	}
}
