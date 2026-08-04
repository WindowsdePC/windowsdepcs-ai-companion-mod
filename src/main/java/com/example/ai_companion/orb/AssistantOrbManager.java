package com.example.ai_companion.orb;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-authoritative assistant ball with chat, reminders, waypoints, and exploration help. */
public final class AssistantOrbManager implements AutoCloseable {
	private static final String SYSTEM_PROMPT = """
		你是 Minecraft 世界中的 AI 助手球。你只向当前玩家提供简洁、安全、可执行的探索建议。
		不要声称看见观察信息之外的方块或实体，不要编造坐标，不要请求管理员权限，不要输出服务器命令。
		回复使用玩家提问的语言，最多六句话；若信息不足，明确说明并建议玩家如何确认。
		""";

	private final Supplier<ModConfig> config;
	private final AssistantOrbStore store;
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Set<UUID> chatting = new HashSet<>();
	private long lastReminderCheckTick = Long.MIN_VALUE;

	public AssistantOrbManager(Supplier<ModConfig> config) {
		this(config, AssistantOrbStore.load());
	}

	AssistantOrbManager(Supplier<ModConfig> config, AssistantOrbStore store) {
		this.config = config;
		this.store = store;
	}

	public void chat(MinecraftServer server, ServerPlayer player, String message) {
		String question = requireText(message, 500, "消息");
		synchronized (chatting) {
			if (!chatting.add(player.getUUID())) throw new IllegalStateException("AI助手球正在思考");
		}
		String observation = context(player) + "\n玩家问题=" + question;
		client.chat(config.get(), SYSTEM_PROMPT, observation).whenComplete((answer, error) ->
			server.execute(() -> {
				synchronized (chatting) {
					chatting.remove(player.getUUID());
				}
				if (error != null) {
					AiCompanionMod.LOGGER.error("Assistant-orb request failed for {}", player.getScoreboardName(), error);
					player.sendSystemMessage(Component.literal("[AI助手球] 请求失败：" + rootMessage(error)));
					return;
				}
				player.sendSystemMessage(Component.literal("[AI助手球] " + answer));
			}));
	}

	public AssistantWaypoint saveWaypoint(ServerPlayer player, String name) throws IOException {
		return store.saveWaypoint(player.getUUID(), new AssistantWaypoint(name,
			player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
			System.currentTimeMillis()));
	}

	public List<AssistantWaypoint> waypoints(ServerPlayer player) {
		return store.waypoints(player.getUUID());
	}

	public boolean removeWaypoint(ServerPlayer player, String name) throws IOException {
		return store.removeWaypoint(player.getUUID(), name);
	}

	public AssistantReminder remind(ServerPlayer player, int minutes, String message) throws IOException {
		String text = requireText(message, 200, "提醒内容");
		long delay = Duration.ofMinutes(minutes).toMillis();
		return store.addReminder(player.getUUID(), Math.addExact(System.currentTimeMillis(), delay), text);
	}

	public List<AssistantReminder> reminders(ServerPlayer player) {
		return store.reminders(player.getUUID());
	}

	public boolean cancelReminder(ServerPlayer player, long id) throws IOException {
		return store.cancelReminder(player.getUUID(), id);
	}

	public String explorationSummary(ServerPlayer player) {
		String dimension = player.level().dimension().identifier().toString();
		List<AssistantWaypoint> sameDimension = waypoints(player).stream()
			.filter(waypoint -> waypoint.dimension().equals(dimension))
			.sorted((first, second) -> Double.compare(distanceSquared(player, first), distanceSquared(player, second)))
			.limit(3).toList();
		String nearest = sameDimension.isEmpty() ? "本维度没有已保存坐标"
			: "最近坐标：" + sameDimension.stream().map(waypoint -> waypoint.name() + " "
				+ Math.round(Math.sqrt(distanceSquared(player, waypoint))) + "格").toList();
		return String.format(Locale.ROOT, "维度 %s · 当前位置 X %.1f, Y %.1f, Z %.1f · %s",
			dimension, player.getX(), player.getY(), player.getZ(), nearest);
	}

	public void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (lastReminderCheckTick != Long.MIN_VALUE && tick - lastReminderCheckTick < 20) return;
		lastReminderCheckTick = tick;
		try {
			List<java.util.UUID> onlinePlayers = server.getPlayerList().getPlayers().stream()
				.map(ServerPlayer::getUUID).toList();
			for (AssistantOrbStore.DueReminder due : store.takeDue(System.currentTimeMillis(), onlinePlayers)) {
				ServerPlayer player = server.getPlayerList().getPlayer(due.playerId());
				if (player != null) {
					player.sendSystemMessage(Component.literal("[AI助手球提醒 #" + due.reminder().id() + "] "
						+ due.reminder().message()));
				}
			}
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot update assistant-orb reminders", error);
		}
	}

	private String context(ServerPlayer player) {
		List<AssistantWaypoint> waypoints = waypoints(player);
		String saved = waypoints.isEmpty() ? "无" : waypoints.stream().limit(10)
			.map(AssistantWaypoint::displayText).toList().toString();
		return "玩家=" + player.getScoreboardName() + "，维度="
			+ player.level().dimension().identifier() + "，位置=("
			+ String.format(Locale.ROOT, "%.1f,%.1f,%.1f", player.getX(), player.getY(), player.getZ())
			+ ")，已保存坐标=" + saved;
	}

	private static double distanceSquared(ServerPlayer player, AssistantWaypoint waypoint) {
		double dx = player.getX() - waypoint.x();
		double dy = player.getY() - waypoint.y();
		double dz = player.getZ() - waypoint.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private static String requireText(String value, int maxLength, String label) {
		String normalized = value == null ? "" : value.strip();
		if (normalized.isBlank() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "必须为 1-" + maxLength + " 个字符");
		}
		return normalized;
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@Override
	public void close() {
		synchronized (chatting) {
			chatting.clear();
		}
		store.close();
	}
}
