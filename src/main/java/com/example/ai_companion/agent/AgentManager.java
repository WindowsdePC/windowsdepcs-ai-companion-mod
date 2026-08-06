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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns fake-player lifecycles and applies allow-listed AI actions. */
public final class AgentManager implements AutoCloseable {
	private static final long EYE_COOLDOWN_TICKS = 20L * 60L;
	public static final int DEFAULT_AUTOMATIC_INTERVAL_TICKS = 200;
	public static final int MIN_AUTOMATIC_INTERVAL_TICKS = 100;
	public static final int MAX_AUTOMATIC_INTERVAL_TICKS = 72_000;

	private static final class Agent {
		final String name;
		final FakePlayer player;
		final String textureValue;
		final String textureSignature;
		final long createdAtEpochMillis;
		final AtomicBoolean thinking = new AtomicBoolean();
		AgentMode mode = AgentMode.IDLE;
		String targetName = "";
		String promptId = "";
		long nextEyeTick;
		EyeSnapshot eyeSnapshot;
		double remainingX;
		double remainingZ;
		boolean arenaLocked;
		boolean automaticEnabled;
		boolean furnitureSeated;
		int automaticIntervalTicks = DEFAULT_AUTOMATIC_INTERVAL_TICKS;
		long nextAutomaticTick;

		Agent(String name, FakePlayer player, String textureValue, String textureSignature,
				long createdAtEpochMillis) {
			this.name = name;
			this.player = player;
			this.textureValue = textureValue == null ? "" : textureValue;
			this.textureSignature = textureSignature == null ? "" : textureSignature;
			this.createdAtEpochMillis = Math.max(0, createdAtEpochMillis);
		}
	}

	private final Map<String, Agent> agents = new LinkedHashMap<>();
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Supplier<ModConfig> config;
	private final PromptStore prompts;
	private final AgentIdentityStore identityStore = AgentIdentityStore.load();
	private Function<String, String> collaborationContext = ignored -> "";
	private BiFunction<String, String, String> promptDecorator = (ignored, prompt) -> prompt;
	private long nextIdentitySaveTick;

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

	public record AgentIdentity(String name, UUID uuid, String dimension, double x, double y, double z,
			AgentMode mode, int completedAdvancements, long createdAtEpochMillis) {
		public String displayText() {
			return "%s · %s · %s (%.1f, %.1f, %.1f) · 进度 %d".formatted(name, uuid, dimension,
				x, y, z, completedAdvancements);
		}
	}

	public record AutomaticStatus(String name, boolean enabled, int intervalTicks, long ticksUntilNext,
			boolean thinking) {
		public String displayText() {
			return "%s · 自动决策=%s · 间隔=%dt (%.1fs) · 下次=%dt%s".formatted(name,
				enabled ? "开启" : "关闭", intervalTicks, intervalTicks / 20.0,
				Math.max(0, ticksUntilNext), thinking ? " · 正在思考" : "");
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
		agents.put(key, new Agent(name, bot, textureValue, textureSignature, System.currentTimeMillis()));
		saveIdentities();
		return bot;
	}

	/** Restores durable AI identities after all server levels and advancement data are available. */
	public synchronized void restore(MinecraftServer server) {
		for (AgentIdentityStore.StoredAgent stored : identityStore.entries()) {
			if (agents.containsKey(stored.name().toLowerCase())) continue;
			try {
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(stored.dimension()));
				ServerLevel level = server.getLevel(dimension);
				if (level == null) level = server.overworld();
				GameProfile profile = new GameProfile(UUID.fromString(stored.uuid()), stored.name());
				if (!stored.textureValue().isBlank()) {
					profile.properties().put("textures", new Property("textures", stored.textureValue(),
						stored.textureSignature()));
				}
				FakePlayer bot = FakePlayer.get(level, profile);
				bot.setPos(stored.x(), stored.y(), stored.z());
				bot.setCustomName(Component.literal(stored.name()));
				bot.setCustomNameVisible(true);
				level.addNewPlayer(bot);
				Agent agent = new Agent(stored.name(), bot, stored.textureValue(), stored.textureSignature(),
					stored.createdAtEpochMillis());
				agent.mode = AgentMode.valueOf(stored.mode());
				agent.targetName = stored.targetName();
				agent.promptId = stored.promptId();
				agent.automaticEnabled = stored.automaticEnabled();
				agent.automaticIntervalTicks = stored.automaticIntervalTicks();
				agent.nextAutomaticTick = server.getTickCount() + agent.automaticIntervalTicks;
				agents.put(stored.name().toLowerCase(), agent);
			} catch (RuntimeException error) {
				AiCompanionMod.LOGGER.error("Cannot restore AI identity {}", stored.name(), error);
			}
		}
		AiCompanionMod.LOGGER.info("Restored {} persistent AI player identities", agents.size());
	}

	public synchronized boolean remove(String name) {
		Agent agent = agents.remove(name.toLowerCase());
		if (agent == null) return false;
		agent.player.getAdvancements().save();
		agent.player.discard();
		saveIdentities();
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

	public synchronized boolean hasAgent(String name) {
		return name != null && agents.containsKey(name.toLowerCase());
	}

	public synchronized String canonicalName(String name) {
		return requireAgent(name).name;
	}

	public synchronized void setCollaborationContext(Function<String, String> provider) {
		collaborationContext = provider == null ? ignored -> "" : provider;
	}

	/** Lets feature modules add per-agent identity context without replacing prompt presets. */
	public synchronized void setPromptDecorator(BiFunction<String, String, String> decorator) {
		promptDecorator = decorator == null ? (ignored, prompt) -> prompt : decorator;
	}

	public synchronized AgentIdentity identity(String name, MinecraftServer server) {
		Agent agent = requireAgent(name);
		int completed = (int) server.getAdvancements().getAllAdvancements().stream()
			.filter(advancement -> agent.player.getAdvancements().getOrStartProgress(advancement).isDone())
			.count();
		return new AgentIdentity(agent.name, agent.player.getUUID(),
			agent.player.level().dimension().identifier().toString(), agent.player.getX(), agent.player.getY(),
			agent.player.getZ(), agent.mode, completed, agent.createdAtEpochMillis);
	}

	public synchronized java.util.List<String> completedAdvancements(String name, MinecraftServer server) {
		Agent agent = requireAgent(name);
		return server.getAdvancements().getAllAdvancements().stream()
			.filter(advancement -> agent.player.getAdvancements().getOrStartProgress(advancement).isDone())
			.map(advancement -> advancement.id().toString()).sorted().toList();
	}

	public synchronized AutomaticStatus configureAutomatic(String name, boolean enabled, int intervalTicks,
			long currentTick) {
		Agent agent = requireAgent(name);
		agent.automaticEnabled = enabled;
		agent.automaticIntervalTicks = Math.clamp(intervalTicks, MIN_AUTOMATIC_INTERVAL_TICKS,
			MAX_AUTOMATIC_INTERVAL_TICKS);
		agent.nextAutomaticTick = currentTick + agent.automaticIntervalTicks;
		saveIdentities();
		return automaticStatus(agent, currentTick);
	}

	public synchronized AutomaticStatus automaticStatus(String name, long currentTick) {
		return automaticStatus(requireAgent(name), currentTick);
	}

	public synchronized Collection<AutomaticStatus> automaticStatuses(long currentTick) {
		return agents.values().stream().map(agent -> automaticStatus(agent, currentTick)).toList();
	}

	/** Prevents normal prompt movement from fighting with the server-authoritative arena controller. */
	public synchronized void setArenaLocked(String name, boolean locked) {
		Agent agent = requireAgent(name);
		agent.arenaLocked = locked;
		if (locked) {
			agent.furnitureSeated = false;
			agent.player.setShiftKeyDown(false);
		}
		agent.remainingX = 0;
		agent.remainingZ = 0;
	}

	public synchronized void setPrompt(String name, String promptId) {
		Agent agent = requireAgent(name);
		if (!prompts.contains(promptId)) throw new IllegalArgumentException("找不到提示词预设: " + promptId);
		agent.promptId = PromptStore.validateId(promptId);
		saveIdentities();
	}

	public synchronized void clearPrompt(String name) {
		requireAgent(name).promptId = "";
		saveIdentities();
	}

	public synchronized void setMode(String name, AgentMode mode, String targetName, long currentTick) {
		Agent agent = requireAgent(name);
		agent.furnitureSeated = false;
		agent.player.setShiftKeyDown(false);
		agent.mode = mode;
		agent.targetName = mode == AgentMode.IDLE ? "" : targetName;
		agent.eyeSnapshot = null;
		agent.nextEyeTick = currentTick + EYE_COOLDOWN_TICKS;
		saveIdentities();
	}

	public synchronized void seatAtFurniture(String name, ServerLevel level, BlockPos position) {
		Agent agent = requireAgent(name);
		if (agent.arenaLocked) throw new IllegalStateException("该 AI 正在参加竞技场比赛");
		if (agent.player.level() != level) throw new IllegalStateException("AI 与沙发不在同一维度");
		agent.remainingX = 0;
		agent.remainingZ = 0;
		agent.player.setPos(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5);
		agent.player.setShiftKeyDown(true);
		agent.furnitureSeated = true;
	}

	public synchronized void standFromFurniture(String name) {
		Agent agent = requireAgent(name);
		if (!agent.furnitureSeated) throw new IllegalStateException("该 AI 尚未坐在家具上");
		agent.furnitureSeated = false;
		agent.player.setShiftKeyDown(false);
		agent.remainingX = 0;
		agent.remainingZ = 0;
	}

	public synchronized boolean isSeatedAtFurniture(String name) {
		return requireAgent(name).furnitureSeated;
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
		requestDecision(server, agent, instruction, result);
	}

	private void requestDecision(MinecraftServer server, Agent agent, String instruction,
			Consumer<String> result) {
		long now = server.getTickCount();
		String eye = agent.eyeSnapshot == null ? "天眼快照=无"
			: agent.eyeSnapshot.promptText(now);
		String observation = "名字=%s，模式=%s，维度=%s，位置=(%.1f,%.1f,%.1f)，任务=%s，%s".formatted(
			agent.name, agent.mode, agent.player.level().dimension().identifier(), agent.player.getX(),
			agent.player.getY(), agent.player.getZ(), instruction, eye);
		String cooperation = collaborationContext.apply(agent.name);
		if (!cooperation.isBlank()) observation += "，" + cooperation;
		try {
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
		} catch (RuntimeException error) {
			agent.thinking.set(false);
			throw error;
		}
	}

	public synchronized void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now >= nextIdentitySaveTick) {
			nextIdentitySaveTick = now + 200;
			saveIdentities();
		}
		for (Agent agent : agents.values()) {
			if (agent.mode != AgentMode.IDLE && now >= agent.nextEyeTick) {
				captureEye(server, agent, now);
			}
			if (agent.arenaLocked) continue;
			if (agent.automaticEnabled && now >= agent.nextAutomaticTick) {
				agent.nextAutomaticTick = now + agent.automaticIntervalTicks;
				startAutomaticDecision(server, agent);
			}
			if (agent.furnitureSeated) continue;
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

	private void startAutomaticDecision(MinecraftServer server, Agent agent) {
		if (!config.get().hasApiKey() || !agent.thinking.compareAndSet(false, true)) return;
		String instruction = switch (agent.mode) {
			case HUNTER -> "根据追杀目标、当前观察和天眼快照自主决定下一步；优先正常移动与侦察。";
			case TEAMMATE -> "根据队友位置、当前观察和风险自主决定下一步；优先跟随、保护或报告。";
			case PVP_COACH -> "根据训练对象和当前状态自主决定下一步安全训练动作或建议。";
			case IDLE -> "根据当前环境自主决定一个简短、正常且不作弊的下一步。";
		};
		requestDecision(server, agent, instruction, message -> {
			if (message.startsWith("AI 请求失败")) {
				AiCompanionMod.LOGGER.warn("Automatic decision failed for {}: {}", agent.name, message);
			}
		});
	}

	private AutomaticStatus automaticStatus(Agent agent, long currentTick) {
		return new AutomaticStatus(agent.name, agent.automaticEnabled, agent.automaticIntervalTicks,
			agent.automaticEnabled ? Math.max(0, agent.nextAutomaticTick - currentTick) : 0,
			agent.thinking.get());
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
			return promptDecorator.apply(agent.name,
				PromptTemplates.applyTargets(prompts.get(agent.promptId), agent.targetName));
		}
		String prompt = switch (agent.mode) {
			case HUNTER -> PromptTemplates.applyTargets(prompts.get("hunter"), agent.targetName);
			case TEAMMATE -> PromptTemplates.applyTargets(prompts.get("teammate"), agent.targetName);
			case PVP_COACH -> PromptTemplates.applyTargets(prompts.get("pvp_coach"), agent.targetName);
			case IDLE -> PromptTemplates.applyTargets(prompts.get("idle"), agent.targetName);
		};
		return promptDecorator.apply(agent.name, prompt);
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
		if (decision.action().equals("move") && !agent.furnitureSeated) {
			agent.remainingX = decision.dx();
			agent.remainingZ = decision.dz();
		}
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private synchronized void saveIdentities() {
		try {
			identityStore.replace(agents.values().stream().map(agent -> new AgentIdentityStore.StoredAgent(
				agent.name, agent.player.getUUID().toString(),
				agent.player.level().dimension().identifier().toString(), agent.player.getX(), agent.player.getY(),
				agent.player.getZ(), agent.mode.name(), agent.targetName, agent.promptId,
				agent.textureValue, agent.textureSignature, agent.createdAtEpochMillis,
				agent.automaticEnabled, agent.automaticIntervalTicks)).toList());
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot save persistent AI identities", error);
		}
	}

	@Override
	public synchronized void close() {
		saveIdentities();
		agents.values().forEach(agent -> agent.player.getAdvancements().save());
		agents.values().forEach(agent -> agent.player.discard());
		agents.clear();
	}
}
