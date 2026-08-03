package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers the /aiplayer command tree. */
public final class AiPlayerCommands {
	private static final Map<UUID, Long> LAST_MINIGAME_REWARD_TIME = new HashMap<>();
	private static final long MINIGAME_REWARD_COOLDOWN_MILLIS = 60_000L;
	private AiPlayerCommands() {}

	public static void register(AgentManager agents, PromptStore prompts,
								Supplier<ModConfig> config, Consumer<ModConfig> update,
								Supplier<GameplayConfig> gameplay, Consumer<GameplayConfig> updateGameplay) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer")
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> create(c.getSource(), agents, StringArgumentType.getString(c, "name"), null, null))))
				.then(Commands.literal("create-many")
					.then(Commands.argument("baseName", StringArgumentType.word())
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
							.executes(c -> createMany(c.getSource(), agents,
								StringArgumentType.getString(c, "baseName"),
								IntegerArgumentType.getInteger(c, "count"))))))
				.then(Commands.literal("create-textured").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("textureValue", StringArgumentType.word())
						.then(Commands.argument("textureSignature", StringArgumentType.word())
							.executes(c -> create(c.getSource(), agents, StringArgumentType.getString(c, "name"),
								StringArgumentType.getString(c, "textureValue"),
								StringArgumentType.getString(c, "textureSignature")))))))
				.then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> remove(c.getSource(), agents, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("list").executes(c -> list(c.getSource(), agents)))
				.then(Commands.literal("idle").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> mode(c.getSource(), agents, StringArgumentType.getString(c, "name"),
						AgentMode.IDLE, ""))))
				.then(Commands.literal("hunt").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("target", StringArgumentType.word())
						.executes(c -> mode(c.getSource(), agents, StringArgumentType.getString(c, "name"),
							AgentMode.HUNTER, StringArgumentType.getString(c, "target"))))))
				.then(Commands.literal("team").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("target", StringArgumentType.word())
						.executes(c -> mode(c.getSource(), agents, StringArgumentType.getString(c, "name"),
							AgentMode.TEAMMATE, StringArgumentType.getString(c, "target"))))))
				.then(Commands.literal("coach").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("target", StringArgumentType.word())
						.executes(c -> mode(c.getSource(), agents, StringArgumentType.getString(c, "name"),
							AgentMode.PVP_COACH, StringArgumentType.getString(c, "target"))))))
				.then(Commands.literal("eye").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> eye(c.getSource(), agents, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("ask").then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("instruction", StringArgumentType.greedyString())
						.executes(c -> ask(c.getSource(), agents, StringArgumentType.getString(c, "name"),
							StringArgumentType.getString(c, "instruction"))))))
				.then(Commands.literal("prompt")
					.then(Commands.literal("list")
						.executes(c -> promptList(c.getSource(), prompts)))
					.then(Commands.literal("show")
						.then(Commands.argument("id", StringArgumentType.word())
							.executes(c -> promptShow(c.getSource(), prompts,
								StringArgumentType.getString(c, "id")))))
					.then(Commands.literal("put")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("id", StringArgumentType.word())
							.then(Commands.argument("base64", StringArgumentType.greedyString())
								.executes(c -> promptPut(c.getSource(), prompts,
									StringArgumentType.getString(c, "id"),
									StringArgumentType.getString(c, "base64"))))))
					.then(Commands.literal("remove")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("id", StringArgumentType.word())
							.executes(c -> promptRemove(c.getSource(), prompts,
								StringArgumentType.getString(c, "id")))))
					.then(Commands.literal("reset")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("id", StringArgumentType.word())
							.executes(c -> promptReset(c.getSource(), prompts,
								StringArgumentType.getString(c, "id")))))
					.then(Commands.literal("assign")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("agent", StringArgumentType.word())
							.then(Commands.argument("id", StringArgumentType.word())
								.executes(c -> promptAssign(c.getSource(), agents,
									StringArgumentType.getString(c, "agent"),
									StringArgumentType.getString(c, "id"))))))
					.then(Commands.literal("clear")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("agent", StringArgumentType.word())
							.executes(c -> promptClear(c.getSource(), agents,
								StringArgumentType.getString(c, "agent"))))))
				.then(Commands.literal("config")
					.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("status").executes(c -> status(c.getSource(), config.get())))
					.then(Commands.literal("endpoint").then(Commands.argument("url", StringArgumentType.greedyString())
						.executes(c -> save(c.getSource(), config.get().withApiBase(
							StringArgumentType.getString(c, "url")), update))))
					.then(Commands.literal("model").then(Commands.argument("model", StringArgumentType.word())
						.executes(c -> save(c.getSource(), config.get().withModel(
							StringArgumentType.getString(c, "model")), update))))
					.then(Commands.literal("token").then(Commands.argument("token", StringArgumentType.greedyString())
						.executes(c -> save(c.getSource(), config.get().withApiKey(
							StringArgumentType.getString(c, "token")), update)))))
				.then(Commands.literal("feature")
					.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("status")
						.executes(c -> featureStatus(c.getSource(), gameplay.get())))
					.then(Commands.literal("enabled")
						.then(Commands.argument("value", BoolArgumentType.bool())
							.executes(c -> saveGameplay(c.getSource(), gameplay.get().withEnabled(
								BoolArgumentType.getBool(c, "value")), updateGameplay))))
					.then(Commands.literal("durability-every")
						.then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
							.executes(c -> saveGameplay(c.getSource(), gameplay.get().withDurabilityEvery(
								IntegerArgumentType.getInteger(c, "value")), updateGameplay))))
					.then(Commands.literal("hunger-every")
						.then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
							.executes(c -> saveGameplay(c.getSource(), gameplay.get().withHungerEvery(
								IntegerArgumentType.getInteger(c, "value")), updateGameplay))))
					.then(Commands.literal("hunger-cost")
						.then(Commands.argument("value", IntegerArgumentType.integer(0, 20))
							.executes(c -> saveGameplay(c.getSource(), gameplay.get().withHungerCost(
								IntegerArgumentType.getInteger(c, "value")), updateGameplay))))
					.then(Commands.literal("strength")
						.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 4.0))
							.executes(c -> saveGameplay(c.getSource(), gameplay.get().withRushStrength(
								DoubleArgumentType.getDouble(c, "value")), updateGameplay)))))
				.then(Commands.literal("minigame")
					.then(Commands.literal("reward")
						.then(Commands.literal("tetris")
							.then(Commands.argument("score", IntegerArgumentType.integer(0, 100000))
								.then(Commands.argument("lines", IntegerArgumentType.integer(1, 200))
									.executes(c -> rewardTetris(c.getSource(),
										IntegerArgumentType.getInteger(c, "score"),
										IntegerArgumentType.getInteger(c, "lines"))))))))));
		}

	private static int rewardTetris(CommandSourceStack source, int score, int lines)
			throws CommandSyntaxException {
		var player = source.getPlayerOrException();
		long now = System.currentTimeMillis();
		long last = LAST_MINIGAME_REWARD_TIME.getOrDefault(player.getUUID(), 0L);
		if (now - last < MINIGAME_REWARD_COOLDOWN_MILLIS) {
			source.sendFailure(Component.literal("小游戏矿物奖励仍在冷却中"));
			return 0;
		}
		int minimumPlausibleScore = lines * 100;
		if (score < minimumPlausibleScore || score > 100000) {
			source.sendFailure(Component.literal("俄罗斯方块结算数据无效"));
			return 0;
		}
		LAST_MINIGAME_REWARD_TIME.put(player.getUUID(), now);
		int count = Math.min(3, 1 + lines / 4);
		int roll = source.getLevel().getRandom().nextInt(100);
		int diamondChance = Math.min(20, 3 + lines);
		Item reward = roll < diamondChance ? Items.DIAMOND
			: roll < diamondChance + 27 ? Items.GOLD_INGOT : Items.IRON_INGOT;
		ItemStack stack = new ItemStack(reward, count);
		if (!player.addItem(stack)) player.drop(stack, false);
		source.sendSuccess(() -> Component.literal("俄罗斯方块奖励：" + count + " × "
			+ reward.getName(stack).getString()), false);
		return count;
	}

	private static int createMany(CommandSourceStack source, AgentManager agents,
							  String baseName, int count) throws CommandSyntaxException {
		if (!baseName.matches("[A-Za-z0-9_]{1,13}")) {
			source.sendFailure(Component.literal("批量名称前缀必须为 1-13 位英文字母、数字或下划线"));
			return 0;
		}
		int created = 0;
		for (int index = 1; index <= count; index++) {
			String name = baseName + index;
			try {
				agents.create(source.getPlayerOrException(), name, null, null);
				created++;
			} catch (RuntimeException error) {
				source.sendFailure(Component.literal(name + " 创建失败: " + error.getMessage()));
				break;
			}
		}
		int result = created;
		source.sendSuccess(() -> Component.literal("已批量创建 " + result + " 个 AI"), true);
		return created;
	}

	private static int promptList(CommandSourceStack source, PromptStore prompts) {
		source.sendSuccess(() -> Component.literal("提示词预设: " + String.join(", ", prompts.ids())), false);
		return prompts.ids().size();
	}

	private static int promptShow(CommandSourceStack source, PromptStore prompts, String id) {
		try {
			source.sendSuccess(() -> Component.literal("[" + PromptStore.validateId(id) + "]\n"
				+ prompts.get(id)), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int promptPut(CommandSourceStack source, PromptStore prompts, String id, String encoded) {
		try {
			String prompt = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			prompts.put(id, prompt);
			source.sendSuccess(() -> Component.literal("已保存提示词预设: " + PromptStore.validateId(id)), true);
			return 1;
		} catch (IllegalArgumentException error) {
			source.sendFailure(Component.literal("提示词数据无效: " + error.getMessage()));
			return 0;
		} catch (IOException error) {
			source.sendFailure(Component.literal("提示词保存失败: " + error.getMessage()));
			return 0;
		}
	}

	private static int promptRemove(CommandSourceStack source, PromptStore prompts, String id) {
		try {
			prompts.remove(id);
			source.sendSuccess(() -> Component.literal("已删除提示词预设: " + id), true);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int promptReset(CommandSourceStack source, PromptStore prompts, String id) {
		try {
			prompts.reset(id);
			source.sendSuccess(() -> Component.literal("已恢复内置提示词预设: " + id), true);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int promptAssign(CommandSourceStack source, AgentManager agents, String agent, String id) {
		try {
			agents.setPrompt(agent, id);
			source.sendSuccess(() -> Component.literal("已将提示词 " + id + " 分配给 " + agent), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int promptClear(CommandSourceStack source, AgentManager agents, String agent) {
		try {
			agents.clearPrompt(agent);
			source.sendSuccess(() -> Component.literal("已清除 " + agent + " 的自定义提示词分配"), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int create(CommandSourceStack source, AgentManager agents, String name,
							  String value, String signature) throws CommandSyntaxException {
		if (!name.matches("[A-Za-z0-9_]{3,16}")) {
			source.sendFailure(Component.literal("名称必须为 3-16 位英文字母、数字或下划线"));
			return 0;
		}
		try {
			agents.create(source.getPlayerOrException(), name, value, signature);
			source.sendSuccess(() -> Component.literal("已创建 AI 玩家: " + name), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int remove(CommandSourceStack source, AgentManager agents, String name) {
		if (!agents.remove(name)) {
			source.sendFailure(Component.literal("找不到 AI: " + name));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("已移除 AI: " + name), true);
		return 1;
	}

	private static int list(CommandSourceStack source, AgentManager agents) {
		String names = String.join(", ", agents.views(source.getServer().getTickCount()).stream()
			.map(Object::toString).toList());
		source.sendSuccess(() -> Component.literal(names.isEmpty() ? "当前没有 AI 玩家" : "AI 玩家: " + names), false);
		return 1;
	}

	private static int mode(CommandSourceStack source, AgentManager agents, String name,
							AgentMode mode, String target) {
		try {
			agents.setMode(name, mode, target, source.getServer().getTickCount());
			String suffix = target.isBlank() ? "" : "，目标: " + target;
			source.sendSuccess(() -> Component.literal("已将 " + name + " 设为 " + mode + suffix), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int eye(CommandSourceStack source, AgentManager agents, String name) {
		try {
			String snapshot = agents.useEyeNow(source.getServer(), name);
			source.sendSuccess(() -> Component.literal(snapshot), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int ask(CommandSourceStack source, AgentManager agents, String name, String instruction) {
		try {
			agents.ask(source.getServer(), name, instruction,
				message -> source.sendSuccess(() -> Component.literal(message), false));
			source.sendSuccess(() -> Component.literal("已发送请求，AI 正在思考……"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int status(CommandSourceStack source, ModConfig config) {
		source.sendSuccess(() -> Component.literal("endpoint=" + config.apiBase() + ", model="
			+ config.model() + ", token=" + (config.hasApiKey() ? "已设置" : "未设置")), false);
		return 1;
	}

	private static int featureStatus(CommandSourceStack source, GameplayConfig config) {
		source.sendSuccess(() -> Component.literal("金矛突进=" + config.goldenSpearRushEnabled()
			+ ", 耐久间隔=" + config.durabilityEvery()
			+ ", 饥饿间隔=" + config.hungerEvery()
			+ ", 饥饿消耗=" + config.hungerCost()
			+ ", 强度=" + config.rushStrength()), false);
		return 1;
	}

	private static int save(CommandSourceStack source, ModConfig updated, Consumer<ModConfig> update) {
		try {
			updated.save();
			update.accept(updated);
			source.sendSuccess(() -> Component.literal("AI 配置已保存（令牌不会显示）"), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("配置保存失败: " + error.getMessage()));
			return 0;
		}
	}

	private static int saveGameplay(CommandSourceStack source, GameplayConfig updated,
								 Consumer<GameplayConfig> update) {
		try {
			updated.save();
			update.accept(updated);
			source.sendSuccess(() -> Component.literal("其他功能配置已保存"), true);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("其他功能配置保存失败: " + error.getMessage()));
			return 0;
		}
	}
}
