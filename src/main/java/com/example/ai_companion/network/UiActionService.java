package com.example.ai_companion.network;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.arena.ArenaMode;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.InventoryShuffleManager;
import com.example.ai_companion.gameplay.MinigameRewardManager;
import com.example.ai_companion.pet.PetAttribute;
import com.example.ai_companion.pet.PetCompetitionEngine;
import com.example.ai_companion.pet.PetCompetitionManager;
import com.example.ai_companion.pet.PetCompetitionMode;
import com.example.ai_companion.pet.PetProfile;
import com.example.ai_companion.orb.AssistantOrbManager;
import com.example.ai_companion.photo.PhotographyManager;
import com.example.ai_companion.travel.TravelLogManager;
import com.example.ai_companion.news.MinecraftDailyNewsManager;
import com.example.ai_companion.livestream.LivestreamManager;
import com.example.ai_companion.music.AiMusicManager;
import com.example.ai_companion.society.AiSocietyManager;
import com.example.ai_companion.weather.WeatherEventManager;
import com.example.ai_companion.spyglass.SpyglassHighlightManager;
import com.example.ai_companion.spyglass.SpyglassTargetCondition;
import com.example.ai_companion.world.WorldFeatureConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Direct Java dispatcher for UI actions. Commands remain available for consoles and automation. */
public final class UiActionService {
	private final AgentManager agents;
	private final PromptStore prompts;
	private final Supplier<ModConfig> config;
	private final Consumer<ModConfig> updateConfig;
	private final Supplier<GameplayConfig> gameplay;
	private final Consumer<GameplayConfig> updateGameplay;
	private final AiArenaManager arena;
	private final PetCompetitionManager pets;
	private final Supplier<WorldFeatureConfig> world;
	private final Consumer<WorldFeatureConfig> updateWorld;
	private final SpyglassHighlightManager spyglass;
	private final AssistantOrbManager orb;
	private final PhotographyManager photography;
	private final TravelLogManager travel;
	private final MinecraftDailyNewsManager news;
	private final LivestreamManager livestreams;
	private final AiMusicManager music;
	private final AiSocietyManager society;
	private final WeatherEventManager weather;
	private final MinigameRewardManager minigameRewards;

	public UiActionService(AgentManager agents, PromptStore prompts, Supplier<ModConfig> config,
			Consumer<ModConfig> updateConfig, Supplier<GameplayConfig> gameplay,
			Consumer<GameplayConfig> updateGameplay, AiArenaManager arena, PetCompetitionManager pets,
			Supplier<WorldFeatureConfig> world, Consumer<WorldFeatureConfig> updateWorld,
			SpyglassHighlightManager spyglass, AssistantOrbManager orb, PhotographyManager photography,
			TravelLogManager travel, MinecraftDailyNewsManager news, LivestreamManager livestreams,
			AiMusicManager music, AiSocietyManager society, WeatherEventManager weather,
			MinigameRewardManager minigameRewards) {
		this.agents = agents;
		this.prompts = prompts;
		this.config = config;
		this.updateConfig = updateConfig;
		this.gameplay = gameplay;
		this.updateGameplay = updateGameplay;
		this.arena = arena;
		this.pets = pets;
		this.world = world;
		this.updateWorld = updateWorld;
		this.spyglass = spyglass;
		this.orb = orb;
		this.photography = photography;
		this.travel = travel;
		this.news = news;
		this.livestreams = livestreams;
		this.music = music;
		this.society = society;
		this.weather = weather;
		this.minigameRewards = minigameRewards;
	}

	public void handle(ServerPlayer player, UiActionPayload request) {
		try {
			switch (request.action()) {
				case "agent.create_many" -> createMany(player, arg(request, 0), integer(request, 1, 1, 20));
				case "agent.teleport_to" -> teleportToAgent(player, arg(request, 0));
				case "agent.mode" -> setMode(player, arg(request, 0), AgentMode.valueOf(arg(request, 1)), arg(request, 2));
				case "agent.idle" -> setMode(player, arg(request, 0), AgentMode.IDLE, "");
				case "agent.ask" -> ask(player, arg(request, 0), arg(request, 1));
				case "prompt.put" -> putPrompt(player, arg(request, 0), arg(request, 1));
				case "prompt.remove" -> mutatePrompt(player, () -> prompts.remove(arg(request, 0)), "提示词已删除");
				case "prompt.reset" -> mutatePrompt(player, () -> prompts.reset(arg(request, 0)), "提示词已恢复");
				case "prompt.assign" -> assignPrompt(player, arg(request, 0), arg(request, 1));
				case "arena.start" -> startArena(player, ArenaMode.valueOf(arg(request, 0)), arg(request, 1));
				case "arena.status" -> reply(player, arena.view(player.level().getServer().getTickCount()).displayText());
				case "arena.stop" -> stopArena(player);
				case "pet.create" -> createPet(player, request);
				case "pet.list" -> listPets(player, false);
				case "pet.leaderboard" -> listPets(player, true);
				case "pet.train" -> trainPet(player, arg(request, 0), PetAttribute.parse(arg(request, 1)));
				case "pet.compete" -> competePets(player, PetCompetitionMode.valueOf(arg(request, 0)),
					arg(request, 1), arg(request, 2));
				case "config.save" -> saveApi(player, request);
				case "config.read" -> sendApiSnapshot(player);
				case "config.status" -> reply(player, "API=" + config.get().apiBase() + "，模型="
					+ config.get().model() + "，令牌=" + (config.get().hasApiKey() ? "已配置" : "未配置"));
				case "gameplay.save" -> saveGameplay(player, request);
				case "inventory.shuffle" -> shuffle(player);
				case "spyglass.save" -> saveSpyglass(player, request);
				case "world.save" -> saveWorld(player, request);
				case "album.list" -> reply(player, photography.photos(player).stream().limit(8)
					.map(value -> value.displayText()).collect(java.util.stream.Collectors.joining(" | ", "相册：", "")));
				case "travel.stats" -> reply(player, "旅行日志：" + travel.categoryCounts(player));
				case "news.today" -> reply(player, news.generateCurrent(player.level().getServer()).summaryText());
				case "live.status" -> reply(player, describe(livestreams.status(player)));
				case "music.status" -> reply(player, describe(music.status(player)));
				case "society.leaderboard" -> reply(player, society.leaderboard().stream().limit(8)
					.map(value -> value.agentName() + "=" + value.balance()).collect(java.util.stream.Collectors.joining(", ", "社会排行：", "")));
				case "weather.status" -> reply(player, weather.active() == null ? "当前没有自然事件"
					: weather.active().type().displayName() + " · 剩余 " + weather.active().remainingSeconds() + " 秒");
				case "weather.config" -> reply(player, "自然事件配置：" + weather.settings()
					+ " · 权重=" + weather.typeWeightSummary());
				case "orb.explore" -> reply(player, orb.explorationSummary(player));
				case "feature.status" -> reply(player, "服务端直连 UI、小游戏、宠物竞技、AI竞技、导航、望远镜与世界功能可用");
				case "minigame.tetris.start" -> minigameResult(player,
					minigameRewards.startTetris(player, arg(request, 0), player.level().getServer().getTickCount()));
				case "minigame.tetris.finish" -> minigameResult(player,
					minigameRewards.finishTetris(player, arg(request, 0), integer(request, 1, 0, 2_000_000),
						integer(request, 2, 0, 200), player.level().getServer().getTickCount()));
				case "minigame.minesweeper.start" -> minigameResult(player,
					minigameRewards.startMinesweeper(player, arg(request, 0), player.level().getServer().getTickCount()));
				case "minigame.minesweeper.finish" -> minigameResult(player,
					minigameRewards.finishMinesweeper(player, arg(request, 0), integer(request, 1, 0, 72_000),
						player.level().getServer().getTickCount()));
				default -> throw new IllegalArgumentException("服务器不支持此 UI 操作: " + request.action());
			}
		} catch (Exception error) {
			result(player, false, "UI 操作失败：" + rootMessage(error));
		}
	}

	private void createMany(ServerPlayer player, String prefix, int count) {
		requireAdmin(player);
		if (!prefix.matches("[A-Za-z0-9_]{1,13}")) throw new IllegalArgumentException("AI 名称前缀无效");
		int created = 0;
		for (int index = 1; index <= count; index++) {
			String name = prefix + index;
			if (name.length() > 16) throw new IllegalArgumentException("生成名称超过 16 字符: " + name);
			agents.create(player, name, null, null);
			created++;
		}
		reply(player, "已通过 UI 在服务端创建 " + created + " 个可见 AI");
	}

	private void teleportToAgent(ServerPlayer player, String name) {
		requireAdmin(player);
		ServerPlayer target = agents.managedPlayer(name);
		player.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(),
			target.getYRot(), target.getXRot(), true);
		reply(player, "已传送至 " + agents.canonicalName(name) + " · "
			+ target.level().dimension().identifier() + " · X " + String.format(java.util.Locale.ROOT, "%.1f", target.getX())
			+ " Y " + String.format(java.util.Locale.ROOT, "%.1f", target.getY())
			+ " Z " + String.format(java.util.Locale.ROOT, "%.1f", target.getZ()));
	}

	private void setMode(ServerPlayer player, String name, AgentMode mode, String target) {
		requireAdmin(player);
		if (mode != AgentMode.IDLE && player.level().getServer().getPlayerList().getPlayerByName(target) == null) {
			throw new IllegalArgumentException("目标玩家当前不在线");
		}
		agents.setMode(name, mode, target, player.level().getServer().getTickCount());
		reply(player, name + " 的模式已设为 " + mode.name().toLowerCase());
	}

	private void ask(ServerPlayer player, String name, String instruction) {
		if (instruction.isBlank() || instruction.length() > 500) throw new IllegalArgumentException("任务长度应为 1-500");
		agents.ask(player.level().getServer(), name, instruction, message -> reply(player, message));
		reply(player, "AI 请求已由服务端接收");
	}

	private void putPrompt(ServerPlayer player, String id, String text) throws IOException {
		requireAdmin(player);
		prompts.put(id, text);
		reply(player, "提示词已直接保存到服务器: " + id);
	}

	private void mutatePrompt(ServerPlayer player, IoAction action, String success) throws IOException {
		requireAdmin(player);
		action.run();
		reply(player, success);
	}

	private void assignPrompt(ServerPlayer player, String name, String id) {
		requireAdmin(player);
		agents.setPrompt(name, id);
		reply(player, "已分配提示词 " + id + " 给 " + name);
	}

	private void startArena(ServerPlayer player, ArenaMode mode, String names) {
		requireAdmin(player);
		List<String> participants = Arrays.stream(names.strip().split("[\\s,]+"))
			.filter(value -> !value.isBlank()).toList();
		reply(player, arena.start(player.level().getServer(), mode, participants).displayText());
	}

	private void stopArena(ServerPlayer player) {
		requireAdmin(player);
		if (!arena.stop(player.level().getServer(), "比赛已由 UI 管理员停止")) throw new IllegalStateException("当前没有比赛");
		reply(player, "AI 竞技场已停止");
	}

	private void createPet(ServerPlayer player, UiActionPayload request) throws IOException {
		PetProfile pet = pets.create(player.getUUID(), player.getScoreboardName(), arg(request, 0),
			integer(request, 1, 10, 100), integer(request, 2, 10, 100), integer(request, 3, 10, 100));
		reply(player, "已创建竞技宠物：" + describe(pet));
	}

	private void listPets(ServerPlayer player, boolean leaderboard) {
		List<PetProfile> values = leaderboard ? pets.leaderboard() : pets.ownedBy(player.getUUID());
		if (values.isEmpty()) {
			reply(player, leaderboard ? "竞技排行榜暂无记录" : "你还没有竞技宠物");
			return;
		}
		for (int index = 0; index < values.size(); index++) {
			PetProfile pet = values.get(index);
			reply(player, (leaderboard ? "#" + (index + 1) + " " : "") + describe(pet));
		}
	}

	private void trainPet(ServerPlayer player, String name, PetAttribute attribute) throws IOException {
		PetProfile pet = pets.train(player.getUUID(), name, attribute, System.currentTimeMillis());
		reply(player, "训练完成：" + describe(pet));
	}

	private void competePets(ServerPlayer player, PetCompetitionMode mode, String first, String second)
			throws IOException {
		PetCompetitionEngine.Result result = pets.compete(mode, first, second,
			player.level().getServer().getTickCount() ^ System.nanoTime());
		reply(player, mode.displayName() + "结果：" + result.winner().name() + " 战胜 "
			+ result.loser().name() + "（" + result.winnerScore() + ":" + result.loserScore() + "）");
	}

	private void saveApi(ServerPlayer player, UiActionPayload request) throws IOException {
		requireAdmin(player);
		ModConfig changed = config.get().withApiBase(arg(request, 0)).withModel(arg(request, 1));
		if (request.arguments().size() > 2 && !arg(request, 2).isBlank()) changed = changed.withApiKey(arg(request, 2));
		changed.save();
		updateConfig.accept(changed);
		sendApiSnapshot(player);
	}

	private void sendApiSnapshot(ServerPlayer player) {
		ModConfig current = config.get();
		var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
		String base = encoder.encodeToString(current.apiBase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String model = encoder.encodeToString(current.model().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		reply(player, "@config\t" + base + "\t" + model + "\t" + current.hasApiKey());
	}

	private void saveGameplay(ServerPlayer player, UiActionPayload request) throws IOException {
		requireAdmin(player);
		GameplayConfig changed = gameplay.get().withEnabled(bool(request, 0))
			.withDurabilityEvery(integer(request, 1, 1, 1000))
			.withHungerEvery(integer(request, 2, 1, 1000))
			.withHungerCost(integer(request, 3, 0, 20))
			.withRushStrength(decimal(request, 4, 0.1, 4.0))
			.withFlexibleEquipmentEnabled(bool(request, 5));
		changed.save();
		updateGameplay.accept(changed);
		reply(player, "游戏增强设置已直接保存到服务器");
	}

	private void shuffle(ServerPlayer player) {
		if (!gameplay.get().flexibleEquipmentEnabled()) throw new IllegalStateException("请先开启任意物品装备");
		reply(player, "已安全打乱 " + InventoryShuffleManager.shuffle(player) + " 个非快捷栏物品");
	}

	private void saveSpyglass(ServerPlayer player, UiActionPayload request) {
		var changed = spyglass.settings(player.getUUID()).withEnabled(bool(request, 0))
			.withRadiusChunks(integer(request, 1, 1, 32))
			.withHoldSeconds(integer(request, 2, 1, 10))
			.withEffectSeconds(integer(request, 3, 1, 600))
			.withTargetCondition(SpyglassTargetCondition.parse(arg(request, 4)))
			.withCooldownSeconds(integer(request, 5, 1, 600))
			.withMaxTargets(integer(request, 6, 1, 1024));
		spyglass.update(player.getUUID(), changed);
		reply(player, "望远镜发光设置已直接保存到服务器");
	}

	private void saveWorld(ServerPlayer player, UiActionPayload request) throws IOException {
		requireAdmin(player);
		WorldFeatureConfig changed = world.get().withNavigatorEnabled(bool(request, 0))
			.withMercifulVoidEnabled(bool(request, 1)).withMaximumWorldBorderEnabled(bool(request, 2));
		changed.save();
		updateWorld.accept(changed);
		reply(player, "世界功能设置已直接保存到服务器");
	}

	private void minigameResult(ServerPlayer player, MinigameRewardManager.Result value) {
		result(player, value.accepted(), value.message().getString());
	}

	private static String describe(PetProfile pet) {
		return pet.name() + " · 主人=" + pet.ownerName() + " · 速度=" + pet.speed() + " · 力量="
			+ pet.strength() + " · 耐力=" + pet.endurance() + " · 胜负=" + pet.wins() + "/" + pet.losses();
	}

	private static String describe(com.example.ai_companion.livestream.LivestreamSession value) {
		return "直播：" + (value.enabled() ? "开启" : "关闭") + " · AI=" + value.viewers()
			+ " · 间隔=" + (value.intervalTicks() / 20) + "秒 · 弹幕=" + value.commentsGenerated();
	}

	private static String describe(com.example.ai_companion.music.MusicSession value) {
		return "合奏：" + value.style().displayName() + " · AI=" + value.members() + " · 音符=" + value.notesPlayed();
	}

	private static String arg(UiActionPayload request, int index) {
		if (index < 0 || index >= request.arguments().size()) throw new IllegalArgumentException("UI 参数不完整");
		return request.arguments().get(index);
	}

	private static int integer(UiActionPayload request, int index, int minimum, int maximum) {
		int value = Integer.parseInt(arg(request, index));
		if (value < minimum || value > maximum) throw new IllegalArgumentException("UI 数值超出范围");
		return value;
	}

	private static double decimal(UiActionPayload request, int index, double minimum, double maximum) {
		double value = Double.parseDouble(arg(request, index));
		if (!Double.isFinite(value) || value < minimum || value > maximum) throw new IllegalArgumentException("UI 数值超出范围");
		return value;
	}

	private static boolean bool(UiActionPayload request, int index) {
		String value = arg(request, index);
		if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("UI 开关值无效");
		return Boolean.parseBoolean(value);
	}

	private static void requireAdmin(ServerPlayer player) {
		if (!player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)) throw new SecurityException("需要管理员权限");
	}

	private static void reply(ServerPlayer player, String message) {
		result(player, true, message);
	}

	private static void result(ServerPlayer player, boolean success, String message) {
		if (ServerPlayNetworking.canSend(player, UiActionResultPayload.TYPE)) {
			ServerPlayNetworking.send(player, new UiActionResultPayload(success, message));
		} else {
			// Compatibility fallback for clients older than the direct UI result protocol.
			player.sendSystemMessage(Component.literal(message));
		}
	}

	private static String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@FunctionalInterface
	private interface IoAction { void run() throws IOException; }
}
