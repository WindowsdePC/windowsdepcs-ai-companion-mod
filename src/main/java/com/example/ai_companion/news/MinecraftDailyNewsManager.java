package com.example.ai_companion.news;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Collects server-observed player/world/AI events and publishes persistent Minecraft Daily issues. */
public final class MinecraftDailyNewsManager implements AutoCloseable {
	private static final int SAMPLE_INTERVAL_TICKS = 100;
	private static final int MAX_EVENTS_PER_SECTION = 16;
	private static final String AI_EDITOR_PROMPT = """
		你是 Minecraft 日报编辑。只能使用提供的玩家事件、世界事件和 AI 事件撰写日报，禁止编造。
		保留三类事件的事实差异；没有事件的栏目明确写“暂无记录”。使用玩家所用语言，输出简短标题和正文，
		最多十二行，不输出服务器命令、坐标猜测或未提供的战况。
		""";

	private record PlayerSnapshot(String name, String dimension) { }

	private final Supplier<ModConfig> config;
	private final AgentManager agents;
	private final AiArenaManager arena;
	private final DailyNewsStore store;
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Map<UUID, PlayerSnapshot> players = new HashMap<>();
	private final Map<String, String> agentStates = new HashMap<>();
	private final Map<String, String> weatherStates = new HashMap<>();
	private final Set<UUID> editing = new HashSet<>();
	private long currentDay = -1;
	private boolean arenaActive;

	public MinecraftDailyNewsManager(Supplier<ModConfig> config, AgentManager agents,
			AiArenaManager arena) {
		this(config, agents, arena, DailyNewsStore.load());
	}

	MinecraftDailyNewsManager(Supplier<ModConfig> config, AgentManager agents,
			AiArenaManager arena, DailyNewsStore store) {
		this.config = config;
		this.agents = agents;
		this.arena = arena;
		this.store = store;
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % SAMPLE_INTERVAL_TICKS != 0) return;
		long day = currentReportDay();
		if (currentDay < 0) {
			currentDay = day;
			record(day, NewsCategory.WORLD, "Minecraft日报开始记录：" + LocalDate.ofEpochDay(day));
		} else if (day != currentDay) {
			try {
				createIssue(currentDay);
			} catch (IOException error) {
				AiCompanionMod.LOGGER.error("Cannot close Minecraft Daily day {}", currentDay, error);
			}
			currentDay = day;
			record(day, NewsCategory.WORLD, "日期变更为 " + LocalDate.ofEpochDay(day));
		}
		samplePlayers(server, day);
		sampleWorld(server, day);
		sampleAgents(server, day);
	}

	public DailyNewsIssue generateCurrent(MinecraftServer server) throws IOException {
		long day = currentReportDay();
		if (currentDay < 0) currentDay = day;
		return createIssue(day);
	}

	public List<DailyNewsIssue> issues() {
		return store.issues();
	}

	public DailyNewsIssue requireIssue(long id) {
		return store.requireIssue(id);
	}

	public void requestAiEdition(MinecraftServer server, ServerPlayer player, long issueId) {
		DailyNewsIssue issue = store.requireIssue(issueId);
		synchronized (editing) {
			if (!editing.add(player.getUUID())) throw new IllegalStateException("已有日报正在等待 AI 编辑");
		}
		String material = "日报标题=" + issue.title() + "\n日期=" + LocalDate.ofEpochDay(issue.minecraftDay())
			+ "\n事件材料：\n" + issue.body();
		client.chat(config.get(), AI_EDITOR_PROMPT, material).whenComplete((answer, error) ->
			server.execute(() -> {
				synchronized (editing) {
					editing.remove(player.getUUID());
				}
				if (error != null) {
					AiCompanionMod.LOGGER.error("Daily-news AI edit failed for issue {}", issueId, error);
					player.sendSystemMessage(Component.literal("[Minecraft日报] AI编辑失败："
						+ rootMessage(error)));
					return;
				}
				try {
					DailyNewsIssue updated = store.saveAiEdition(issueId, answer);
					player.sendSystemMessage(Component.literal("[Minecraft日报 #" + updated.id()
						+ "] AI版已保存\n" + updated.aiEdition()));
				} catch (IOException | RuntimeException saveError) {
					AiCompanionMod.LOGGER.error("Cannot save AI edition for issue {}", issueId, saveError);
					player.sendSystemMessage(Component.literal("[Minecraft日报] 保存AI版失败："
						+ saveError.getMessage()));
				}
			}));
	}

	private DailyNewsIssue createIssue(long day) throws IOException {
		List<NewsEvent> events = store.eventsForDay(day);
		String body = buildBody(events);
		String title = events.isEmpty() ? "平静的一天" : "世界、玩家与 AI 的一天";
		return store.upsertIssue(day, title, body);
	}

	static String buildBody(List<NewsEvent> events) {
		StringBuilder result = new StringBuilder();
		for (NewsCategory category : NewsCategory.values()) {
			result.append("【").append(category.displayName()).append("】\n");
			List<String> messages = events.stream().filter(event -> event.category() == category)
				.limit(MAX_EVENTS_PER_SECTION).map(NewsEvent::message).toList();
			if (messages.isEmpty()) result.append("- 暂无记录\n");
			else messages.forEach(message -> result.append("- ").append(message).append('\n'));
		}
		return result.toString().stripTrailing();
	}

	private void samplePlayers(MinecraftServer server, long day) {
		Map<UUID, PlayerSnapshot> online = new LinkedHashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String dimension = player.level().dimension().identifier().toString();
			PlayerSnapshot snapshot = new PlayerSnapshot(player.getScoreboardName(), dimension);
			online.put(player.getUUID(), snapshot);
			PlayerSnapshot previous = players.get(player.getUUID());
			if (previous == null) {
				record(day, NewsCategory.PLAYER, snapshot.name() + " 上线，当前位于 " + dimension);
			} else if (!previous.dimension().equals(dimension)) {
				record(day, NewsCategory.PLAYER, snapshot.name() + " 从 " + previous.dimension()
					+ " 前往 " + dimension);
			}
		}
		for (Map.Entry<UUID, PlayerSnapshot> previous : players.entrySet()) {
			if (!online.containsKey(previous.getKey())) {
				record(day, NewsCategory.PLAYER, previous.getValue().name() + " 离开服务器");
			}
		}
		players.clear();
		players.putAll(online);
	}

	private void sampleWorld(MinecraftServer server, long day) {
		for (ServerLevel level : server.getAllLevels()) {
			String dimension = level.dimension().identifier().toString();
			String weather = level.isThundering() ? "雷暴" : level.isRaining() ? "降雨" : "晴朗";
			String previous = weatherStates.put(dimension, weather);
			if (previous != null && !previous.equals(weather)) {
				record(day, NewsCategory.WORLD, dimension + " 的天气从 " + previous + " 变为 " + weather);
			}
		}
	}

	private void sampleAgents(MinecraftServer server, long day) {
		Map<String, String> current = new LinkedHashMap<>();
		for (AgentManager.AgentView agent : agents.views(server.getTickCount())) {
			String state = agent.mode().name() + ":" + agent.targetName();
			current.put(agent.name(), state);
			String previous = agentStates.get(agent.name());
			if (previous == null) {
				record(day, NewsCategory.AI, "AI " + agent.name() + " 加入世界，模式="
					+ agent.mode().name().toLowerCase());
			} else if (!previous.equals(state)) {
				record(day, NewsCategory.AI, "AI " + agent.name() + " 切换为 "
					+ agent.mode().name().toLowerCase()
					+ (agent.targetName().isBlank() ? "" : "，目标=" + agent.targetName()));
			}
		}
		for (String name : new ArrayList<>(agentStates.keySet())) {
			if (!current.containsKey(name)) record(day, NewsCategory.AI, "AI " + name + " 离开世界");
		}
		agentStates.clear();
		agentStates.putAll(current);

		boolean active = arena.view(server.getTickCount()).active();
		if (active != arenaActive) {
			record(day, NewsCategory.AI, active ? "AI 竞技场比赛开始" : "AI 竞技场比赛结束");
			arenaActive = active;
		}
	}

	private void record(long day, NewsCategory category, String message) {
		try {
			store.record(day, category, message);
		} catch (IOException | RuntimeException error) {
			AiCompanionMod.LOGGER.error("Cannot record Minecraft Daily event: {}", message, error);
		}
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private static long currentReportDay() {
		return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
	}

	@Override
	public void close() {
		players.clear();
		agentStates.clear();
		weatherStates.clear();
		synchronized (editing) {
			editing.clear();
		}
		store.close();
	}
}
