package com.example.ai_companion.command;

import com.example.ai_companion.pet.PetAttribute;
import com.example.ai_companion.pet.PetCompetitionEngine;
import com.example.ai_companion.pet.PetCompetitionManager;
import com.example.ai_companion.pet.PetCompetitionMode;
import com.example.ai_companion.pet.PetProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Commands for persistent AI pet training, races and battles. */
public final class PetCompetitionCommands {
	private PetCompetitionCommands() { }

	public static void register(PetCompetitionManager pets) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer").then(Commands.literal("pet")
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("speed", IntegerArgumentType.integer(10, 100))
						.then(Commands.argument("strength", IntegerArgumentType.integer(10, 100))
							.then(Commands.argument("endurance", IntegerArgumentType.integer(10, 100))
								.executes(c -> create(c.getSource(), pets,
									StringArgumentType.getString(c, "name"),
									IntegerArgumentType.getInteger(c, "speed"),
									IntegerArgumentType.getInteger(c, "strength"),
									IntegerArgumentType.getInteger(c, "endurance"))))))))
				.then(Commands.literal("list").executes(c -> list(c.getSource(), pets)))
				.then(Commands.literal("train").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("attribute", StringArgumentType.word())
						.executes(c -> train(c.getSource(), pets, StringArgumentType.getString(c, "name"),
							PetAttribute.parse(StringArgumentType.getString(c, "attribute")))))))
				.then(Commands.literal("race").then(duelArguments(pets, PetCompetitionMode.RACE)))
				.then(Commands.literal("battle").then(duelArguments(pets, PetCompetitionMode.BATTLE)))
				.then(Commands.literal("leaderboard").executes(c -> leaderboard(c.getSource(), pets))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> duelArguments(
			PetCompetitionManager pets, PetCompetitionMode mode) {
		return Commands.argument("first", StringArgumentType.word())
			.then(Commands.argument("second", StringArgumentType.word()).executes(c -> compete(c.getSource(), pets, mode,
				StringArgumentType.getString(c, "first"), StringArgumentType.getString(c, "second"))));
	}

	private static int create(CommandSourceStack source, PetCompetitionManager pets, String name,
			int speed, int strength, int endurance) {
		try {
			ServerPlayer owner = source.getPlayerOrException();
			PetProfile pet = pets.create(owner.getUUID(), owner.getScoreboardName(), name, speed, strength, endurance);
			return success(source, "已创建竞技宠物：" + describe(pet));
		} catch (Exception error) { return failure(source, error); }
	}

	private static int list(CommandSourceStack source, PetCompetitionManager pets) {
		try {
			List<PetProfile> owned = pets.ownedBy(source.getPlayerOrException().getUUID());
			if (owned.isEmpty()) return success(source, "你还没有竞技宠物");
			owned.forEach(pet -> source.sendSuccess(() -> Component.literal(describe(pet)), false));
			return owned.size();
		} catch (Exception error) { return failure(source, error); }
	}

	private static int train(CommandSourceStack source, PetCompetitionManager pets,
			String name, PetAttribute attribute) {
		try {
			PetProfile pet = pets.train(source.getPlayerOrException().getUUID(), name, attribute,
				System.currentTimeMillis());
			return success(source, pet.name() + " 的" + attribute.displayName() + "提升至 " + switch (attribute) {
				case SPEED -> pet.speed(); case STRENGTH -> pet.strength(); case ENDURANCE -> pet.endurance();
			});
		} catch (Exception error) { return failure(source, error); }
	}

	private static int compete(CommandSourceStack source, PetCompetitionManager pets,
			PetCompetitionMode mode, String first, String second) {
		try {
			PetCompetitionEngine.Result result = pets.compete(mode, first, second,
				source.getServer().getTickCount() ^ System.nanoTime());
			return success(source, mode.displayName() + "结果：" + result.winner().name() + " 战胜 "
				+ result.loser().name() + "（" + result.winnerScore() + ":" + result.loserScore() + "）");
		} catch (Exception error) { return failure(source, error); }
	}

	private static int leaderboard(CommandSourceStack source, PetCompetitionManager pets) {
		List<PetProfile> board = pets.leaderboard();
		if (board.isEmpty()) return success(source, "竞技排行榜暂无记录");
		for (int index = 0; index < board.size(); index++) {
			PetProfile pet = board.get(index);
			int rank = index + 1;
			source.sendSuccess(() -> Component.literal("#" + rank + " " + pet.name() + " · 主人="
				+ pet.ownerName() + " · 胜负=" + pet.wins() + "/" + pet.losses() + " · 评分=" + pet.rating()), false);
		}
		return board.size();
	}

	private static String describe(PetProfile pet) {
		return pet.name() + " · 速度=" + pet.speed() + " · 力量=" + pet.strength()
			+ " · 耐力=" + pet.endurance() + " · 胜负=" + pet.wins() + "/" + pet.losses();
	}

	private static int success(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), false); return 1;
	}

	private static int failure(CommandSourceStack source, Exception error) {
		source.sendFailure(Component.literal("宠物竞技操作失败：" + error.getMessage())); return 0;
	}
}
