package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.ai.AiDecision;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.ai.PromptTemplates;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns fake-player lifecycles and applies allow-listed AI actions. */
public final class AgentManager implements AutoCloseable {
	private static final long EYE_COOLDOWN_TICKS = 20L * 60L;

	private static final class Agent {
		final String name;
		final FakePlayer player;
		final AtomicBoolean thinking = new AtomicBoolean();
		AgentMode mode = AgentMode.IDLE;
		String targetName = "";
		String promptId = "";
		long nextEyeTick;
		EyeSnapshot eyeSnapshot;
		double remainingX;
		double remainingZ;
		boolean arenaLocked;

		Agent(String name, FakePlayer player) {
			this.name = name;
			this.player = player;
		}
	}

	private final Map<String, Agent> agents = new LinkedHashMap<>();
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Supplier<ModConfig> config;
	private final PromptStore prompts;

	public record AgentView(String name, AgentMode mode, String targetName, boolean thinking,
							String promptId, String eyeSummary) {
		@Override
		public String toString() {
			String target = targetName.isBlank() ? "" : ", target=" + targetName;
			String state = thinking ? ", thinking" : "";
			String prompt = promptId.isBlank() ? "" : ", prompt=" + promptId;
			return name + " [" + mode.name().toLowerCase() + target + prompt + state + ", " + eyeSummary + "]";
		}
	}

	public AgentManager(Supplier<ModConfig> config, PromptStore prompts) {
		this.config = config;
		this.prompts = prompts;
	}

	public synchronized FakePlayer create(ServerPlayer owner, String name,
										  String textureValue, String textureSignature) {
		String key = name.toLowerCase();
		if (agents.containsKey(key)) throw new IllegalArgumentException("AI 名称已存在: " + name);
		UUID uuid = UUID.nameUUIDFromBytes(("ai-companion:" + name).getBytes(StandardCharsets.UTF_8));
		GameProfile profile = new GameProfile(uuid, name);
		if (textureValue != null && !textureValue.isBlank()) {
			profile.properties().put("textures", new Property("textures", textureValue, textureSignature));
		}
		ServerLevel level = owner.level();
		FakePlayer bot = FakePlayer.get(level, profile);
		bot.setPos(owner.getX(), owner.getY(), owner.getZ());
		bot.setCustomName(Component.literal(name));
		bot.setCustomNameVisible(true);
		level.addNewPlayer(bot);
		agents.put(key, new Agent(name, bot));
		return bot;
	}

	public synchronized boolean remove(String name) {
		Agent agent = agents.remove(name.toLowerCase());
		if (agent == null) return false;
		agent.player.discard();
		return true;
	}

	public synchronized Collection<AgentView> views(long currentTick) {
		return agents.values().stream().map(agent -> new AgentView(
			agent.name, agent.mode, agent.targetName, agent.thinking.get(),
			agent.promptId,
			agent.eyeSnapshot == null ? "no-eye-snapshot"
				: "eye-age=" + Math.max(0, currentTick - agent.eyeSnapshot.capturedAtTick()) + "t"
		)).toList();
	}

	/** Captures the current server-authoritative location of every managed AI player. */
	public synchronized Collection<AgentPosition> positions() {
		return agents.values().stream().map(agent -> new AgentPosition(
			agent.name,
			agent.player.level().dimension().identifier().toString(),
			agent.player.getX(),
			agent.player.getY(),
			agent.player.getZ()
		)).toList();
	}

	/** Returns one managed fake player for server-side feature integrations such as the arena. */
	public synchronized FakePlayer managedPlayer(String name) {
		return requireAgent(name).player;
	}

	/** Prevents normal prompt movement from fighting with the server-authoritative arena controller. */
	public synchronized void setArenaLocked(String name, boolean locked) {
		Agent agent = requireAgent(name);
		agent.arenaLocked = locked;
		agent.remainingX = 0;
		agent.remainingZ = 0;
	}

	public synchronized void setPrompt(String name, String promptId) {
		Agent agent = requireAgent(name);
		if (!prompts.contains(promptId)) throw new IllegalArgumentException("找不到提示词预设: " + promptId);
		agent.promptId = PromptStore.validateId(promptId);
	}

	public synchronized void clearPrompt(String name) {
		requireAgent(name).promptId = "";
	}

	public synchronized void setMode(String name, AgentMode mode, String targetName, long currentTick) {
		Agent agent = requireAgent(name);
		agent.mode = mode;
		agent.targetName = mode == AgentMode.IDLE ? "" : targetName;
		agent.eyeSnapshot = null;
		agent.nextEyeTick = currentTick + EYE_COOLDOWN_TICKS;
	}

	public synchronized String useEyeNow(MinecraftServer server, String name) {
		Agent agent = requireAgent(name);
		if (agent.targetName.isBlank()) throw new IllegalStateException("该 AI 当前没有目标");
		long now = server.getTickCount();
		if (now < agent.nextEyeTick) {
			throw new IllegalStateException("天眼冷却中，还需 " + (agent.nextEyeTick - now) + " tick");
		}
		if (!captureEye(server, agent, now)) throw new IllegalStateException("目标玩家当前不在线");
		return agent.eyeSnapshot.promptText(now);
	}

	public void ask(MinecraftServer server, String name, String instruction, Consumer<String> result) {
		Agent agent;
		synchronized (this) {
			agent = agents.get(name.toLowerCase());
		}
		if (agent == null) throw new IllegalArgumentException("找不到 AI: " + name);
		if (agent.arenaLocked) throw new IllegalStateException("该 AI 正在参加竞技场比赛");
		if (!agent.thinking.compareAndSet(false, true)) throw new IllegalStateException("该 AI 正在思考");

		long now = server.getTickCount();
		String eye = agent.eyeSnapshot == null ? "天眼快照=无"
			: agent.eyeSnapshot.promptText(now);
		String observation = "名字=%s，模式=%s，维度=%s，位置=(%.1f,%.1f,%.1f)，任务=%s，%s".formatted(
			agent.name, agent.mode, agent.player.level().dimension().identifier(), agent.player.getX(),
			agent.player.getY(), agent.player.getZ(), instruction, eye);
		client.decide(config.get(), promptFor(agent), observation).whenComplete((decision, error) ->
			server.execute(() -> {
				agent.thinking.set(false);
				if (error != null) {
					AiCompanionMod.LOGGER.error("AI request failed for {}", agent.name, error);
					result.accept("AI 请求失败: " + rootMessage(error));
					return;
				}
				apply(server, agent, decision);
				result.accept("AI " + agent.name + " 已执行: " + decision.action());
			}));
	}

	public synchronized void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (Agent agent : agents.values()) {
			if (agent.mode != AgentMode.IDLE && now >= agent.nextEyeTick) {
				captureEye(server, agent, now);
			}
			if (agent.arenaLocked) continue;
			double distance = Math.hypot(agent.remainingX, agent.remainingZ);
			if (distance < 0.01) continue;
			double step = Math.min(0.18, distance);
			double dx = agent.remainingX / distance * step;
			double dz = agent.remainingZ / distance * step;
			agent.player.move(MoverType.SELF, new Vec3(dx, 0, dz));
			agent.remainingX -= dx;
			agent.remainingZ -= dz;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			agent.player.setYRot(yaw);
			agent.player.setYHeadRot(yaw);
		}
	}

	private boolean captureEye(MinecraftServer server, Agent agent, long now) {
		ServerPlayer target = server.getPlayerList().getPlayerByName(agent.targetName);
		agent.nextEyeTick = now + EYE_COOLDOWN_TICKS;
		if (target == null) return false;
		agent.eyeSnapshot = new EyeSnapshot(
			target.getGameProfile().name(),
			target.level().dimension().identifier().toString(),
			target.getX(), target.getY(), target.getZ(), now);
		return true;
	}

	private String promptFor(Agent agent) {
		if (!agent.promptId.isBlank()) {
			return PromptTemplates.applyTargets(prompts.get(agent.promptId), agent.targetName);
		}
		return switch (agent.mode) {
			case HUNTER -> PromptTemplates.applyTargets(prompts.get("hunter"), agent.targetName);
			case TEAMMATE -> PromptTemplates.applyTargets(prompts.get("teammate"), agent.targetName);
			case PVP_COACH -> PromptTemplates.applyTargets(prompts.get("pvp_coach"), agent.targetName);
			case IDLE -> PromptTemplates.applyTargets(prompts.get("idle"), agent.targetName);
		};
	}

	private Agent requireAgent(String name) {
		Agent agent = agents.get(name.toLowerCase());
		if (agent == null) throw new IllegalArgumentException("找不到 AI: " + name);
		return agent;
	}

	private synchronized void apply(MinecraftServer server, Agent agent, AiDecision decision) {
		if (!decision.say().isBlank()) {
			server.getPlayerList().broadcastSystemMessage(
				Component.literal("<" + agent.name + "> " + decision.say()), false);
		}
		if (decision.action().equals("move")) {
			agent.remainingX = decision.dx();
			agent.remainingZ = decision.dz();
		}
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@Override
	public synchronized void close() {
		agents.values().forEach(agent -> agent.player.discard());
		agents.clear();
	}
}
