package com.example.ai_companion.livestream;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Generates rate-limited, fact-grounded AI commentary for consenting players. */
public final class LivestreamManager implements AutoCloseable {
	private static final String COMMENT_PROMPT = """
		你是一名正在观看 Minecraft 玩家直播的 AI 伙伴。只能根据给出的服务器事实生成一条简短弹幕，
		不得编造看不见的敌人、建筑、物品或事件。可以评价明确提供的移动、生命、饥饿、维度和手持物品。
		只输出一行自然语言，最多 80 个字符，不输出命令、坐标推断或角色名前缀。
		""";

	private final Supplier<ModConfig> config;
	private final AgentManager agents;
	private final LivestreamStore store;
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Map<UUID, Long> nextCommentTick = new HashMap<>();
	private final Set<UUID> generating = new HashSet<>();

	public LivestreamManager(Supplier<ModConfig> config, AgentManager agents) {
		this(config, agents, LivestreamStore.load());
	}

	LivestreamManager(Supplier<ModConfig> config, AgentManager agents, LivestreamStore store) {
		this.config = config;
		this.agents = agents;
		this.store = store;
	}

	public LivestreamSession start(ServerPlayer player, List<String> viewers, int intervalTicks)
			throws IOException {
		List<String> normalized = viewers.stream().map(agents::canonicalName).distinct().toList();
		LivestreamSession previous;
		try { previous = store.require(player.getUUID()); }
		catch (IllegalArgumentException missing) { previous = null; }
		LivestreamSession session = new LivestreamSession(player.getUUID(), player.getScoreboardName(),
			normalized, intervalTicks, previous == null ? 0 : previous.commentsGenerated(), true);
		store.put(session);
		nextCommentTick.remove(player.getUUID());
		return session;
	}

	public LivestreamSession status(ServerPlayer player) {
		return store.require(player.getUUID());
	}

	public LivestreamSession setInterval(ServerPlayer player, int intervalTicks) throws IOException {
		LivestreamSession updated = store.require(player.getUUID()).withIntervalTicks(intervalTicks);
		store.put(updated);
		nextCommentTick.remove(player.getUUID());
		return updated;
	}

	public boolean stop(ServerPlayer player) throws IOException {
		nextCommentTick.remove(player.getUUID());
		return store.remove(player.getUUID());
	}

	public void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (LivestreamSession session : store.sessions()) {
			if (!session.enabled()) continue;
			ServerPlayer target = server.getPlayerList().getPlayer(session.playerId());
			if (target == null) continue;
			long due = nextCommentTick.getOrDefault(session.playerId(), now + session.intervalTicks());
			nextCommentTick.putIfAbsent(session.playerId(), due);
			if (now < due || !config.get().hasApiKey()) continue;
			nextCommentTick.put(session.playerId(), now + session.intervalTicks());
			synchronized (generating) {
				if (!generating.add(session.playerId())) continue;
			}
			requestComment(server, target, session);
		}
	}

	private void requestComment(MinecraftServer server, ServerPlayer target, LivestreamSession session) {
		String viewer = session.nextViewer();
		String held = target.getMainHandItem().isEmpty() ? "空手"
			: target.getMainHandItem().getHoverName().getString();
		String observation = String.format(Locale.ROOT,
			"弹幕作者=%s，玩家=%s，维度=%s，位置=(%.1f,%.1f,%.1f)，生命=%.1f，饥饿=%d，主手=%s",
			viewer, target.getScoreboardName(), target.level().dimension().identifier(), target.getX(),
			target.getY(), target.getZ(), target.getHealth(), target.getFoodData().getFoodLevel(), held);
		client.chat(config.get(), COMMENT_PROMPT, observation).whenComplete((answer, error) ->
			server.execute(() -> {
				synchronized (generating) { generating.remove(session.playerId()); }
				if (error != null) {
					AiCompanionMod.LOGGER.warn("Livestream comment failed for {}: {}",
						target.getScoreboardName(), rootMessage(error));
					return;
				}
				ServerPlayer online = server.getPlayerList().getPlayer(session.playerId());
				if (online == null) return;
				try {
					LivestreamSession current = store.require(session.playerId());
					if (!current.enabled()) return;
					String comment = sanitizeComment(answer);
					online.sendSystemMessage(Component.literal("[直播弹幕] <" + viewer + "> " + comment));
					store.put(current.afterComment());
				} catch (IOException | RuntimeException saveError) {
					AiCompanionMod.LOGGER.error("Cannot finish livestream comment", saveError);
				}
			}));
	}

	static String sanitizeComment(String value) {
		String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
		if (safe.length() > 80) safe = safe.substring(0, 80).stripTrailing() + "…";
		return safe.isBlank() ? "继续加油！" : safe;
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@Override
	public void close() {
		nextCommentTick.clear();
		synchronized (generating) { generating.clear(); }
		store.close();
	}
}
