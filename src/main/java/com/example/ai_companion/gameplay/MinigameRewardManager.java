package com.example.ai_companion.gameplay;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Issues bounded minigame rewards directly into the player's inventory on the server and rejects
 * duplicate, implausibly fast or replayed client submissions. Minigames remain playable without a
 * server connection, but physical items are never created client-side. If the inventory is full,
 * the remaining stack is dropped beside the player.
 */
public final class MinigameRewardManager {
	private static final int MINIMUM_SESSION_TICKS = 200;
	private static final int REWARD_COOLDOWN_TICKS = 1_200;
	private static final int SESSION_EXPIRY_TICKS = 24_000;
	private static final String SESSION_PATTERN = "[a-f0-9]{32}";

	private static final int MINIMUM_MINESWEEPER_TICKS = 120;
	private static final int MAXIMUM_MINESWEEPER_TICKS = 24_000;

	private final Map<UUID, Session> tetrisSessions = new HashMap<>();
	private final Map<UUID, Session> minesweeperSessions = new HashMap<>();
	private final Map<UUID, Integer> lastRewardTicks = new HashMap<>();

	public Result startTetris(ServerPlayer player, String sessionId, int currentTick) {
		if (!validSessionId(sessionId)) {
			return Result.failure("小游戏会话标识无效");
		}
		tetrisSessions.put(player.getUUID(), new Session(sessionId, currentTick));
		return Result.success("俄罗斯方块奖励会话已开始");
	}

	public Result finishTetris(ServerPlayer player, String sessionId, int score, int lines,
			int currentTick) {
		Session session = tetrisSessions.get(player.getUUID());
		if (session == null || !session.id().equals(sessionId)) {
			return Result.failure("俄罗斯方块奖励会话不存在或已失效");
		}
		tetrisSessions.remove(player.getUUID());
		int elapsed = currentTick - session.startedAtTick();
		if (elapsed < 0 || elapsed > SESSION_EXPIRY_TICKS) {
			return Result.failure("俄罗斯方块奖励会话已过期");
		}
		if (score < 0 || score > 2_000_000 || lines < 0 || lines > 200) {
			return Result.failure("俄罗斯方块成绩超出有效范围");
		}
		if (lines == 0) {
			return Result.success("本局没有消除方块行，因此不发放矿物奖励");
		}
		if (elapsed < MINIMUM_SESSION_TICKS || lines > elapsed / 20 + 4) {
			return Result.failure("本局用时或消行数未通过奖励校验");
		}
		Integer lastReward = lastRewardTicks.get(player.getUUID());
		if (lastReward != null && currentTick - lastReward < REWARD_COOLDOWN_TICKS) {
			int remainingSeconds = (REWARD_COOLDOWN_TICKS - (currentTick - lastReward) + 19) / 20;
			return Result.success("成绩已记录；矿物奖励冷却还剩 " + remainingSeconds + " 秒");
		}

		Reward reward = rollReward(lines);
		ItemStack stack = new ItemStack(reward.item(), reward.count());
		boolean added = player.addItem(stack);
		if (!added && !stack.isEmpty()) player.drop(stack, false);
		lastRewardTicks.put(player.getUUID(), currentTick);
		return Result.success("俄罗斯方块奖励：" + reward.label() + " × " + reward.count()
			+ "（本局 " + score + " 分，消除 " + lines + " 行）");
	}

	public Result startMinesweeper(ServerPlayer player, String sessionId, int currentTick) {
		if (!validSessionId(sessionId)) return Result.failure("小游戏会话标识无效");
		minesweeperSessions.put(player.getUUID(), new Session(sessionId, currentTick));
		return Result.success("扫雷奖励会话已开始");
	}

	public Result finishMinesweeper(ServerPlayer player, String sessionId, int clientElapsedTicks,
			int currentTick) {
		Session session = minesweeperSessions.get(player.getUUID());
		if (session == null || !session.id().equals(sessionId)) {
			return Result.failure("扫雷奖励会话不存在或已失效");
		}
		minesweeperSessions.remove(player.getUUID());
		int serverElapsed = currentTick - session.startedAtTick();
		if (serverElapsed < MINIMUM_MINESWEEPER_TICKS || serverElapsed > SESSION_EXPIRY_TICKS
				|| clientElapsedTicks < MINIMUM_MINESWEEPER_TICKS
				|| clientElapsedTicks > MAXIMUM_MINESWEEPER_TICKS
				|| clientElapsedTicks > serverElapsed + 100) {
			return Result.failure("扫雷用时未通过奖励校验");
		}
		Integer lastReward = lastRewardTicks.get(player.getUUID());
		if (lastReward != null && currentTick - lastReward < REWARD_COOLDOWN_TICKS) {
			int remainingSeconds = (REWARD_COOLDOWN_TICKS - (currentTick - lastReward) + 19) / 20;
			return Result.success("扫雷胜场已记录；矿物奖励冷却还剩 " + remainingSeconds + " 秒");
		}

		Reward reward = rollMinesweeperReward(clientElapsedTicks);
		ItemStack stack = new ItemStack(reward.item(), reward.count());
		boolean added = player.addItem(stack);
		if (!added && !stack.isEmpty()) player.drop(stack, false);
		lastRewardTicks.put(player.getUUID(), currentTick);
		return Result.success("扫雷奖励：" + reward.label() + " × " + reward.count()
			+ "（用时 " + clientElapsedTicks / 20 + " 秒）");
	}

	private static Reward rollReward(int lines) {
		int roll = ThreadLocalRandom.current().nextInt(100);
		int diamondChance = Math.min(25, 5 + lines / 2);
		if (roll < diamondChance) {
			return new Reward(Items.DIAMOND, lines >= 16 ? 2 : 1, "钻石");
		}
		if (roll < diamondChance + 30) {
			return new Reward(Items.RAW_GOLD, Math.min(5, 1 + lines / 4), "粗金");
		}
		return new Reward(Items.RAW_IRON, Math.min(8, 2 + lines / 2), "粗铁");
	}

	private static Reward rollMinesweeperReward(int elapsedTicks) {
		int roll = ThreadLocalRandom.current().nextInt(100);
		int speedBonus = elapsedTicks <= 1_200 ? 1 : 0;
		if (roll < 12) return new Reward(Items.DIAMOND, 1 + speedBonus, "钻石");
		if (roll < 42) return new Reward(Items.RAW_GOLD, 2 + speedBonus, "粗金");
		return new Reward(Items.RAW_IRON, 4 + speedBonus * 2, "粗铁");
	}

	private static boolean validSessionId(String sessionId) {
		return sessionId != null && sessionId.matches(SESSION_PATTERN);
	}

	public void clear() {
		tetrisSessions.clear();
		minesweeperSessions.clear();
		lastRewardTicks.clear();
	}

	private record Session(String id, int startedAtTick) { }

	private record Reward(Item item, int count, String label) { }

	public record Result(boolean accepted, Component message) {
		static Result success(String message) {
			return new Result(true, Component.literal(message));
		}

		static Result failure(String message) {
			return new Result(false, Component.literal(message));
		}
	}
}
