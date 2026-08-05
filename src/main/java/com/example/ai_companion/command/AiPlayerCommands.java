package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.agent.AgentPosition;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.MinigameRewardManager;
import com.example.ai_companion.gameplay.InventoryShuffleManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers the /aiplayer command tree. */
public final class AiPlayerCommands {
	private AiPlayerCommands() {}

	public static void register(AgentManager agents, PromptStore prompts,
								Supplier<ModConfig> config, Consumer<ModConfig> update,
								Supplier<GameplayConfig> gameplay, Consumer<GameplayConfig> updateGameplay,
								MinigameRewardManager minigameRewards) {
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
				.then(Commands.literal("positions").executes(c -> positions(c.getSource(), agents)))
				.then(Commands.literal("identity").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> identity(c.getSource(), agents,
						StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("advancements").then(Commands.argument("name", StringArgumentType.word())
					.executes(c -> advancements(c.getSource(), agents,
						StringArgumentType.getString(c, "name")))))
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
				.then(Commands.literal("automatic")
					.then(Commands.literal("status")
						.executes(c -> automaticStatuses(c.getSource(), agents))
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(c -> automaticStatus(c.getSource(), agents,
								StringArgumentType.getString(c, "name")))))
					.then(Commands.literal("enable")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(c -> automaticEnable(c.getSource(), agents,
								StringArgumentType.getString(c, "name"), null))
							.then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
								.executes(c -> automaticEnable(c.getSource(), agents,
									StringArgumentType.getString(c, "name"),
									IntegerArgumentType.getInteger(c, "seconds"))))))
					.then(Commands.literal("disable")
						.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(c -> automaticDisable(c.getSource(), agents,
								StringArgumentType.getString(c, "name"))))))
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
								DoubleArgumentType.getDouble(c, "value")), updateGameplay))))
					.then(Commands.literal("flexible-equipment")
						.then(Commands.argument("value", BoolArgumentType.bool())
							.executes(c -> saveGameplay(c.getSource(),
								gameplay.get().withFlexibleEquipmentEnabled(
									BoolArgumentType.getBool(c, "value")), updateGameplay)))))
				.then(Commands.literal("shuffle-inventory")
					.executes(c -> shuffleInventory(c.getSource(), gameplay.get())))
				.then(minigameCommands(minigameRewards))));
		}

	private static int shuffleInventory(CommandSourceStack source, GameplayConfig gameplay)
			throws CommandSyntaxException {
		if (!gameplay.flexibleEquipmentEnabled()) {
			source.getPlayerOrException().sendOverlayMessage(Component.literal("请先开启任意物品装备模式"));
			return 0;
		}
		try {
			int moved = InventoryShuffleManager.shuffle(source.getPlayerOrException());
			source.getPlayerOrException().sendOverlayMessage(Component.literal(
				"已打乱背包栏、装备栏和副手，共移动 " + moved + " 组物品；快捷栏保持不变"));
			return 1;
		} catch (RuntimeException error) {
			source.getPlayerOrException().sendOverlayMessage(Component.literal(
				"物品栏打乱失败：" + error.getMessage()));
			return 0;
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> minigameCommands(
			MinigameRewardManager minigameRewards) {
		return Commands.literal("minigame")
			.then(Commands.literal("start")
				.then(Commands.literal("tetris")
					.then(Commands.argument("session", StringArgumentType.word())
						.executes(c -> startTetris(c.getSource(), minigameRewards,
							StringArgumentType.getString(c, "session")))))
				.then(Commands.literal("minesweeper")
					.then(Commands.argument("session", StringArgumentType.word())
						.executes(c -> startMinesweeper(c.getSource(), minigameRewards,
							StringArgumentType.getString(c, "session"))))))
			.then(Commands.literal("finish")
				.then(Commands.literal("tetris")
					.then(Commands.argument("session", StringArgumentType.word())
						.then(Commands.argument("score", IntegerArgumentType.integer(0, 2_000_000))
							.then(Commands.argument("lines", IntegerArgumentType.integer(0, 200))
								.executes(c -> finishTetris(c.getSource(), minigameRewards,
									StringArgumentType.getString(c, "session"),
									IntegerArgumentType.getInteger(c, "score"),
									IntegerArgumentType.getInteger(c, "lines")))))))
				.then(Commands.literal("minesweeper")
					.then(Commands.argument("session", StringArgumentType.word())
						.then(Commands.argument("elapsedTicks", IntegerArgumentType.integer(1, 24_000))
							.executes(c -> finishMinesweeper(c.getSource(), minigameRewards,
								StringArgumentType.getString(c, "session"),
								IntegerArgumentType.getInteger(c, "elapsedTicks")))))));
	}

	private static int startTetris(CommandSourceStack source, MinigameRewardManager rewards,
			String sessionId) throws CommandSyntaxException {
		MinigameRewardManager.Result result = rewards.startTetris(source.getPlayerOrException(), sessionId,
			source.getServer().getTickCount());
		if (!result.accepted()) showMinigameResult(source, result);
		return result.accepted() ? 1 : 0;
	}

	private static int finishTetris(CommandSourceStack source, MinigameRewardManager rewards,
			String sessionId, int score, int lines) throws CommandSyntaxException {
		MinigameRewardManager.Result result = rewards.finishTetris(source.getPlayerOrException(), sessionId,
			score, lines, source.getServer().getTickCount());
		showMinigameResult(source, result);
		return result.accepted() ? 1 : 0;
	}

	private static int startMinesweeper(CommandSourceStack source, MinigameRewardManager rewards,
			String sessionId) throws CommandSyntaxException {
		MinigameRewardManager.Result result = rewards.startMinesweeper(source.getPlayerOrException(),
			sessionId, source.getServer().getTickCount());
		if (!result.accepted()) showMinigameResult(source, result);
		return result.accepted() ? 1 : 0;
	}

	private static int finishMinesweeper(CommandSourceStack source, MinigameRewardManager rewards,
			String sessionId, int elapsedTicks) throws CommandSyntaxException {
		MinigameRewardManager.Result result = rewards.finishMinesweeper(source.getPlayerOrException(),
			sessionId, elapsedTicks, source.getServer().getTickCount());
		showMinigameResult(source, result);
		return result.accepted() ? 1 : 0;
	}

	private static void showMinigameResult(CommandSourceStack source,
			MinigameRewardManager.Result result) throws CommandSyntaxException {
		// These commands are an internal client/server transport. Keep command feedback out of chat;
		// the short settlement result belongs in the action bar instead.
		source.getPlayerOrException().sendOverlayMessage(result.message());
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

	private static int identity(CommandSourceStack source, AgentManager agents, String name) {
		try {
			AgentManager.AgentIdentity identity = agents.identity(name, source.getServer());
			source.sendSuccess(() -> Component.literal(identity.displayText()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("查询 AI 身份失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int advancements(CommandSourceStack source, AgentManager agents, String name) {
		try {
			java.util.List<String> completed = agents.completedAdvancements(name, source.getServer());
			source.sendSuccess(() -> Component.literal(name + " 已完成 " + completed.size() + " 项原版/模组进度"),
				false);
			completed.stream().limit(20).forEach(id ->
				source.sendSuccess(() -> Component.literal("- " + id), false));
			if (completed.size() > 20) {
				source.sendSuccess(() -> Component.literal("另有 " + (completed.size() - 20) + " 项未显示"), false);
			}
			return completed.size();
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("查询 AI 进度失败：" + error.getMessage()));
			return 0;
		}
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

	private static int positions(CommandSourceStack source, AgentManager agents) {
		var positions = agents.positions();
		if (positions.isEmpty()) {
			source.sendSuccess(() -> Component.literal("当前没有可查询位置的 AI 玩家"), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal("AI 位置（共 " + positions.size() + " 个）："), false);
		for (AgentPosition position : positions) {
			source.sendSuccess(() -> Component.literal(position.displayText()), false);
		}
		return positions.size();
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

	private static int automaticStatuses(CommandSourceStack source, AgentManager agents) {
		var statuses = agents.automaticStatuses(source.getServer().getTickCount());
		if (statuses.isEmpty()) {
			source.sendSuccess(() -> Component.literal("当前没有 AI 玩家"), false);
			return 0;
		}
		statuses.forEach(status -> source.sendSuccess(() -> Component.literal(status.displayText()), false));
		return statuses.size();
	}

	private static int automaticStatus(CommandSourceStack source, AgentManager agents, String name) {
		try {
			var status = agents.automaticStatus(name, source.getServer().getTickCount());
			source.sendSuccess(() -> Component.literal(status.displayText()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int automaticEnable(CommandSourceStack source, AgentManager agents, String name,
			Integer seconds) {
		try {
			long now = source.getServer().getTickCount();
			int interval = seconds == null ? agents.automaticStatus(name, now).intervalTicks() : seconds * 20;
			var status = agents.configureAutomatic(name, true, interval, now);
			source.sendSuccess(() -> Component.literal("已开启：" + status.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int automaticDisable(CommandSourceStack source, AgentManager agents, String name) {
		try {
			long now = source.getServer().getTickCount();
			int interval = agents.automaticStatus(name, now).intervalTicks();
			var status = agents.configureAutomatic(name, false, interval, now);
			source.sendSuccess(() -> Component.literal("已关闭：" + status.displayText()), true);
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
			+ ", 强度=" + config.rushStrength()
			+ ", 任意物品装备=" + config.flexibleEquipmentEnabled()), false);
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
