package com.example.ai_companion.gameplay;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates minigame sessions and gives score-gated, weighted physical rewards on the server.
 * Higher reward tiers require higher scores and remain rarer than lower tiers. Every successful
 * grant is announced in chat with recipient-relative wording ("你" for the winner).
 */
public final class MinigameRewardManager {
	private static final int MINIMUM_SESSION_TICKS = 200;
	private static final int REWARD_COOLDOWN_TICKS = 1_200;
	private static final int SESSION_EXPIRY_TICKS = 24_000;
	private static final String SESSION_PATTERN = "[a-f0-9]{32}";
	private static final int MINIMUM_MINESWEEPER_TICKS = 120;
	private static final int MAXIMUM_MINESWEEPER_TICKS = 24_000;

	private static final List<RewardOption> REWARD_POOL = List.of(
		// Common materials, junk and wooden items.
		option(Items.STRING, 0, 120, 2, 8), option(Items.STICK, 0, 110, 2, 8),
		option(Items.OAK_PLANKS, 0, 100, 2, 6), option(Items.SNOWBALL, 100, 90, 4, 12),
		option(Items.EGG, 150, 80, 2, 8), option(Items.WOODEN_PICKAXE, 200, 55, 1, 1),
		option(Items.WOODEN_SWORD, 250, 50, 1, 1),
		// Coal.
		option(Items.COAL, 350, 70, 2, 8), option(Items.CHARCOAL, 450, 55, 2, 6),
		option(Items.COAL_BLOCK, 700, 22, 1, 2),
		// Copper, from raw material through finished tools.
		option(Items.RAW_COPPER, 800, 58, 2, 8), option(Items.COPPER_NUGGET, 900, 52, 3, 12),
		option(Items.COPPER_INGOT, 1_100, 42, 1, 5), option(Items.COPPER_PICKAXE, 1_400, 22, 1, 1),
		option(Items.COPPER_SWORD, 1_600, 18, 1, 1),
		// Redstone and mechanisms.
		option(Items.REDSTONE, 1_800, 38, 3, 10), option(Items.REDSTONE_TORCH, 2_000, 28, 2, 6),
		option(Items.REPEATER, 2_300, 20, 1, 3), option(Items.COMPARATOR, 2_700, 14, 1, 2),
		option(Items.ENDER_PEARL, 3_000, 12, 1, 3),
		// Iron, including raw and finished forms.
		option(Items.RAW_IRON, 3_200, 32, 2, 7), option(Items.IRON_NUGGET, 3_400, 28, 3, 12),
		option(Items.IRON_INGOT, 3_700, 24, 1, 5), option(Items.IRON_PICKAXE, 4_100, 12, 1, 1),
		option(Items.IRON_SWORD, 4_500, 10, 1, 1), option(Items.RAW_GOLD, 4_800, 12, 1, 4),
		option(Items.GOLD_INGOT, 5_100, 9, 1, 3), option(Items.GOLD_NUGGET, 5_300, 8, 2, 8),
		// Lapis and enchantment-related rewards.
		option(Items.LAPIS_LAZULI, 5_500, 18, 3, 10), option(Items.LAPIS_BLOCK, 6_200, 8, 1, 2),
		option(Items.BOOKSHELF, 6_800, 10, 1, 3), option(Items.EXPERIENCE_BOTTLE, 7_200, 8, 2, 6),
		option(Items.ENCHANTING_TABLE, 8_000, 4, 1, 1), enchantedBook(8_500, 7),
		option(Items.WIND_CHARGE, 9_000, 6, 1, 4),
		// Diamond and netherite.
		option(Items.DIAMOND, 11_000, 6, 1, 3), option(Items.DIAMOND_PICKAXE, 14_000, 3, 1, 1),
		option(Items.DIAMOND_BLOCK, 17_000, 1, 1, 1), option(Items.ANCIENT_DEBRIS, 20_000, 2, 1, 2),
		option(Items.NETHERITE_SCRAP, 24_000, 1, 1, 2),
		option(Items.NETHERITE_INGOT, 32_000, 1, 1, 1)
	);

	private final Map<UUID, Session> tetrisSessions = new HashMap<>();
	private final Map<UUID, Session> minesweeperSessions = new HashMap<>();
	private final Map<UUID, Integer> lastRewardTicks = new HashMap<>();

	public Result startTetris(ServerPlayer player, String sessionId, int currentTick) {
		if (!validSessionId(sessionId)) return Result.failure("小游戏会话标识无效");
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
		if (elapsed < 0 || elapsed > SESSION_EXPIRY_TICKS) return Result.failure("俄罗斯方块奖励会话已过期");
		if (score < 0 || score > 2_000_000 || lines < 0 || lines > 200) {
			return Result.failure("俄罗斯方块成绩超出有效范围");
		}
		if (lines == 0) return Result.success("本局没有消除方块行，因此不发放奖励");
		if (elapsed < MINIMUM_SESSION_TICKS || lines > elapsed / 20 + 4) {
			return Result.failure("本局用时或消行数未通过奖励校验");
		}
		Result cooldown = checkCooldown(player, currentTick, "成绩已记录");
		if (cooldown != null) return cooldown;

		int rewardScore = Math.max(score, lines * 300);
		Reward reward = rollReward(player, rewardScore);
		grantAndAnnounce(player, reward.stack());
		lastRewardTicks.put(player.getUUID(), currentTick);
		return Result.success("俄罗斯方块奖励：" + reward.displayName()
			+ "（奖励分数 " + rewardScore + "，消除 " + lines + " 行）");
	}

	public Result startMinesweeper(ServerPlayer player, String sessionId, int currentTick) {
		if (!validSessionId(sessionId)) return Result.failure("小游戏会话标识无效");
		minesweeperSessions.put(player.getUUID(), new Session(sessionId, currentTick));
		return Result.success("扫雷奖励会话已开始");
	}

	public Result finishMinesweeper(ServerPlayer player, String sessionId, int clientElapsedTicks,
			int currentTick) {
		Session session = minesweeperSessions.get(player.getUUID());
		if (session == null || !session.id().equals(sessionId)) return Result.failure("扫雷奖励会话不存在或已失效");
		minesweeperSessions.remove(player.getUUID());
		int serverElapsed = currentTick - session.startedAtTick();
		if (serverElapsed < MINIMUM_MINESWEEPER_TICKS || serverElapsed > SESSION_EXPIRY_TICKS
				|| clientElapsedTicks < MINIMUM_MINESWEEPER_TICKS
				|| clientElapsedTicks > MAXIMUM_MINESWEEPER_TICKS
				|| clientElapsedTicks > serverElapsed + 100) return Result.failure("扫雷用时未通过奖励校验");
		Result cooldown = checkCooldown(player, currentTick, "扫雷胜场已记录");
		if (cooldown != null) return cooldown;

		int rewardScore = Math.max(500, 9_000 - clientElapsedTicks / 2);
		Reward reward = rollReward(player, rewardScore);
		grantAndAnnounce(player, reward.stack());
		lastRewardTicks.put(player.getUUID(), currentTick);
		return Result.success("扫雷奖励：" + reward.displayName() + "（奖励分数 " + rewardScore
			+ "，用时 " + clientElapsedTicks / 20 + " 秒）");
	}

	private Result checkCooldown(ServerPlayer player, int currentTick, String prefix) {
		Integer lastReward = lastRewardTicks.get(player.getUUID());
		if (lastReward == null || currentTick - lastReward >= REWARD_COOLDOWN_TICKS) return null;
		int remainingSeconds = (REWARD_COOLDOWN_TICKS - (currentTick - lastReward) + 19) / 20;
		return Result.success(prefix + "；奖励冷却还剩 " + remainingSeconds + " 秒");
	}

	private static Reward rollReward(ServerPlayer player, int rewardScore) {
		List<RewardOption> eligible = REWARD_POOL.stream()
			.filter(option -> rewardScore >= option.minimumScore()).toList();
		int totalWeight = eligible.stream().mapToInt(RewardOption::weight).sum();
		int roll = player.getRandom().nextInt(totalWeight);
		RewardOption selected = eligible.getFirst();
		for (RewardOption option : eligible) {
			roll -= option.weight();
			if (roll < 0) {
				selected = option;
				break;
			}
		}

		ItemStack stack;
		int enchantmentLevel = 0;
		if (selected.enchantedBook()) {
			Holder<Enchantment> enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getRandom(player.getRandom()).orElseThrow();
			int maximumLevel = Math.clamp(1 + rewardScore / 1_000, 1, 255);
			enchantmentLevel = 1 + (int) Math.floor(Math.pow(player.getRandom().nextDouble(), 4.0)
				* maximumLevel);
			enchantmentLevel = Math.min(255, enchantmentLevel);
			stack = EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, enchantmentLevel));
		} else {
			int count = selected.minimumCount() + player.getRandom().nextInt(
				selected.maximumCount() - selected.minimumCount() + 1);
			stack = new ItemStack(selected.item(), count);
		}
		String name = stack.getHoverName().getString() + " × " + stack.getCount();
		if (enchantmentLevel > 0) name += "（等级 " + enchantmentLevel + "）";
		return new Reward(stack, name);
	}

	private static void grantAndAnnounce(ServerPlayer player, ItemStack reward) {
		ItemStack granted = reward.copy();
		player.addItem(granted);
		if (!granted.isEmpty()) player.drop(granted, false);
		Component rewardName = reward.getHoverName().copy().append(" × " + reward.getCount());
		player.level().getServer().getPlayerList().broadcastSystemMessage(Component.empty(), viewer ->
			Component.literal(viewer == player ? "你" : player.getName().getString())
				.append("已获得 ").append(rewardName).append(" 奖励"), false);
	}

	private static RewardOption option(Item item, int minimumScore, int weight, int minimumCount,
			int maximumCount) {
		return new RewardOption(item, minimumScore, weight, minimumCount, maximumCount, false);
	}

	private static RewardOption enchantedBook(int minimumScore, int weight) {
		return new RewardOption(Items.ENCHANTED_BOOK, minimumScore, weight, 1, 1, true);
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
	private record RewardOption(Item item, int minimumScore, int weight, int minimumCount,
			int maximumCount, boolean enchantedBook) { }
	private record Reward(ItemStack stack, String displayName) { }

	public record Result(boolean accepted, Component message) {
		static Result success(String message) { return new Result(true, Component.literal(message)); }
		static Result failure(String message) { return new Result(false, Component.literal(message)); }
	}
}
