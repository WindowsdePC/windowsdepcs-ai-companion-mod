package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.ai.AiDecision;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.ai.PromptTemplates;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.voice.AiSpeechService;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
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
		final ServerPlayer player;
		final String textureValue;
		final String textureSignature;
		final long createdAtEpochMillis;
		final AtomicBoolean thinking = new AtomicBoolean();
		AgentMode mode = AgentMode.SURVIVAL;
		String targetName = "";
		String promptId = "";
		long nextEyeTick;
		EyeSnapshot eyeSnapshot;
		double remainingX;
		double remainingZ;
		boolean arenaLocked;
		boolean automaticEnabled;
		boolean furnitureSeated;
		String activeTask = "";
		int automaticIntervalTicks = DEFAULT_AUTOMATIC_INTERVAL_TICKS;
		long nextAutomaticTick;
		long nextWanderTick;
		long lastAttackTick;
		long nextMineTick;
		int blockedMovementTicks;
		String lastMessage = "已生成，正在以生存模式观察附近环境";

		Agent(String name, ServerPlayer player, String textureValue, String textureSignature,
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
	private final AiSpeechService speech = new AiSpeechService();
	private final Supplier<ModConfig> config;
	private final PromptStore prompts;
	private AgentIdentityStore identityStore;
	private Path boundWorldRoot;
	private Function<String, String> collaborationContext = ignored -> "";
	private BiFunction<String, String, String> promptDecorator = (ignored, prompt) -> prompt;
	private BiConsumer<String, AiDecision> actionObserver = (ignored, decision) -> { };
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

	public synchronized ServerPlayer create(ServerPlayer owner, String name,
										  String textureValue, String textureSignature) {
		bindStore(owner.level().getServer());
		String key = name.toLowerCase();
		if (agents.containsKey(key)) throw new IllegalArgumentException("AI 名称已存在: " + name);
		UUID uuid = UUID.nameUUIDFromBytes(("ai-companion:" + name).getBytes(StandardCharsets.UTF_8));
		GameProfile profile = new GameProfile(uuid, name);
		if (textureValue != null && !textureValue.isBlank()) {
			profile.properties().put("textures", new Property("textures", textureValue, textureSignature));
		}
		ServerPlayer bot = VisibleAgentSpawner.spawnNear(owner, profile, agents.size());
		bot.setCustomName(Component.literal(name));
		bot.setCustomNameVisible(true);
		agents.put(key, new Agent(name, bot, textureValue, textureSignature, System.currentTimeMillis()));
		saveIdentities();
		return bot;
	}

	/** Restores durable AI identities after all server levels and advancement data are available. */
	public synchronized void restore(MinecraftServer server) {
		bindStore(server);
		if (!agents.isEmpty()) throw new IllegalStateException("AI manager already contains runtime entities");
		for (AgentIdentityStore.StoredAgent stored : identityStore.entries()) {
			if (agents.containsKey(stored.name().toLowerCase())) continue;
			try {
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(stored.dimension()));
				ServerLevel level = server.getLevel(dimension);
				if (level == null) throw new IllegalStateException("Saved AI dimension is unavailable: " + stored.dimension());
				GameProfile profile = new GameProfile(UUID.fromString(stored.uuid()), stored.name());
				if (!stored.textureValue().isBlank()) {
					profile.properties().put("textures", new Property("textures", stored.textureValue(),
						stored.textureSignature()));
				}
				ServerPlayer bot = VisibleAgentSpawner.restore(server, level, profile,
					stored.x(), stored.y(), stored.z());
				bot.setCustomName(Component.literal(stored.name()));
				bot.setCustomNameVisible(true);
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
		// Remove identities that could not be restored so lists and teleport targets cannot become ghosts.
		saveIdentities();
		AiCompanionMod.LOGGER.info("Restored {} persistent AI player identities", agents.size());
	}

	public synchronized boolean remove(String name) {
		Agent agent = agents.remove(name.toLowerCase());
		if (agent == null) return false;
		agent.player.getAdvancements().save();
		MinecraftServer server = agent.player.level().getServer();
		if (server.getPlayerList().getPlayer(agent.player.getUUID()) == agent.player) {
			server.getPlayerList().remove(agent.player);
		} else {
			agent.player.discard();
		}
		saveIdentities();
		return true;
	}

	public synchronized Collection<AgentView> views(long currentTick) {
		pruneStaleAgents();
		return agents.values().stream().map(agent -> new AgentView(
			agent.name, agent.mode, agent.targetName, agent.thinking.get(),
			agent.promptId,
			agent.eyeSnapshot == null ? "no-eye-snapshot"
				: "eye-age=" + Math.max(0, currentTick - agent.eyeSnapshot.capturedAtTick()) + "t"
		)).toList();
	}

	/** Captures the current server-authoritative location of every managed AI player. */
	public synchronized Collection<AgentPosition> positions() {
		pruneStaleAgents();
		return agents.values().stream().map(agent -> new AgentPosition(
			agent.name,
			agent.player.getUUID().toString(),
			agent.player.level().dimension().identifier().toString(),
			agent.player.getX(),
			agent.player.getY(),
			agent.player.getZ(), agent.player.getHealth(), agent.player.getMaxHealth(),
			agent.player.gameMode.getGameModeForPlayer().getName(), agent.mode, agent.targetName,
			agent.promptId, agent.automaticEnabled, agent.activeTask, agent.lastMessage
		)).toList();
	}

	/** Returns one managed fake player for server-side feature integrations such as the arena. */
	public synchronized ServerPlayer managedPlayer(String name) {
		return requireAgent(name).player;
	}

	public synchronized boolean hasAgent(String name) {
		pruneStaleAgents();
		return name != null && agents.containsKey(name.toLowerCase());
	}

	/** Allows leisure modules to avoid taking control of arena participants. */
	public synchronized boolean isArenaLocked(String name) {
		return requireAgent(name).arenaLocked;
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

	/** Reports successfully applied allow-listed actions to progression modules. */
	public synchronized void setActionObserver(BiConsumer<String, AiDecision> observer) {
		actionObserver = observer == null ? (ignored, decision) -> { } : observer;
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
		// Assigning a prompt is an instruction to use it, not merely to store its ID. Older builds left
		// automatic decisions disabled here, which made a correctly assigned AI keep wandering forever.
		agent.automaticEnabled = true;
		agent.nextAutomaticTick = agent.player.level().getServer().getTickCount() + 1L;
		agent.lastMessage = config.get().hasApiKey()
			? "提示词已生效，自动决策将在下一 Tick 开始"
			: "提示词已分配，但服务器尚未配置 AI API 令牌";
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
		agent.targetName = requiresTarget(mode) ? targetName : "";
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
		if (!config.get().hasApiKey()) throw new IllegalStateException("服务器尚未配置 AI API 令牌");
		if (!agent.thinking.compareAndSet(false, true)) throw new IllegalStateException("该 AI 正在思考");
		synchronized (this) {
			agent.activeTask = instruction.strip();
			agent.automaticEnabled = true;
			agent.nextAutomaticTick = server.getTickCount() + agent.automaticIntervalTicks;
			agent.lastMessage = "正在规划任务：" + bounded(instruction, 160);
		}
		requestDecision(server, agent, instruction, result);
	}

	/** Lets normal chat address an AI with "@Name message", "Name: message" or "Name message". */
	public boolean handlePlayerChat(ServerPlayer sender, String chatText) {
		if (sender == null || chatText == null || chatText.isBlank() || hasAgent(sender.getScoreboardName())) {
			return false;
		}
		String stripped = chatText.strip();
		Agent matched = null;
		String instruction = "";
		synchronized (this) {
			for (Agent candidate : agents.values()) {
				String[] prefixes = {"@" + candidate.name + " ", candidate.name + ": ", candidate.name + " "};
				for (String prefix : prefixes) {
					if (stripped.regionMatches(true, 0, prefix, 0, prefix.length())) {
						matched = candidate;
						instruction = stripped.substring(prefix.length()).strip();
						break;
					}
				}
				if (matched != null) break;
			}
		}
		if (matched == null || instruction.isBlank()) return false;
		String name = matched.name;
		ask(sender.level().getServer(), name, instruction, result ->
			sender.sendSystemMessage(Component.literal("[AI " + name + "] " + result)));
		return true;
	}

	private void requestDecision(MinecraftServer server, Agent agent, String instruction,
			Consumer<String> result) {
		long now = server.getTickCount();
		String eye = agent.eyeSnapshot == null ? "天眼快照=无"
			: agent.eyeSnapshot.promptText(now);
		String observation = "名字=%s，行为模式=%s，游戏模式=%s，生命=%.1f/%.1f，维度=%s，位置=(%.1f,%.1f,%.1f)，持续任务=%s，本轮要求=%s，%s".formatted(
			agent.name, agent.mode, agent.player.gameMode.getGameModeForPlayer().getName(),
			agent.player.getHealth(), agent.player.getMaxHealth(), agent.player.level().dimension().identifier(),
			agent.player.getX(), agent.player.getY(), agent.player.getZ(),
			agent.activeTask.isBlank() ? "无" : agent.activeTask, instruction, eye);
		observation += "，" + nearbyObservation(agent);
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
					if (!decision.say().isBlank()) {
						speech.speak(agent.player, agent.player.getUUID(), decision.say(), config.get());
					}
					String detail = decision.say().isBlank() ? "" : " · " + decision.say();
					result.accept("AI " + agent.name + " 已执行: " + decision.action() + detail);
				}));
		} catch (RuntimeException error) {
			agent.thinking.set(false);
			throw error;
		}
	}

	public synchronized String voiceStatus(String name) {
		Agent agent = requireAgent(name);
		ModConfig current = config.get();
		if (!com.example.ai_companion.voice.VoicechatBridge.available()) {
			return agent.name + " · Simple Voice Chat 未安装或语音 API 尚未连接；文字 AI 仍可用";
		}
		if (!current.apiBase().toLowerCase(java.util.Locale.ROOT).contains("openai.com")) {
			return agent.name + " · 语音模组已连接；当前兼容 API 未提供本模组可用的 PCM TTS，回复保留为文字";
		}
		return agent.name + " · 语音模组与 OpenAI PCM TTS 已就绪；下一条 AI 回复会从实体位置播放";
	}

	public synchronized void tick(MinecraftServer server) {
		pruneStaleAgents();
		pruneFinishedDeaths();
		long now = server.getTickCount();
		if (now >= nextIdentitySaveTick) {
			nextIdentitySaveTick = now + 200;
			saveIdentities();
		}
		for (Agent agent : agents.values()) {
			if (!agent.player.isAlive()) {
				agent.lastMessage = "实体已死亡；可重新创建同名 AI";
				continue;
			}
			if (requiresTarget(agent.mode) && now >= agent.nextEyeTick) {
				captureEye(server, agent, now);
			}
			if (agent.arenaLocked) continue;
			if (agent.automaticEnabled && now >= agent.nextAutomaticTick) {
				agent.nextAutomaticTick = now + agent.automaticIntervalTicks;
				startAutomaticDecision(server, agent);
			}
			if (agent.furnitureSeated) continue;
			tickBuiltInBehaviour(server, agent, now);
			double distance = Math.hypot(agent.remainingX, agent.remainingZ);
			if (distance < 0.01) continue;
			double step = Math.min(0.18, distance);
			double dx = agent.remainingX / distance * step;
			double dz = agent.remainingZ / distance * step;
			double beforeX = agent.player.getX();
			double beforeZ = agent.player.getZ();
			agent.player.move(MoverType.SELF, new Vec3(dx, 0, dz));
			double moved = Math.hypot(agent.player.getX() - beforeX, agent.player.getZ() - beforeZ);
			if (moved < 0.015) {
				agent.blockedMovementTicks++;
				if (agent.blockedMovementTicks >= 12 && now >= agent.nextMineTick) {
					tryMineObstacle(agent, dx, dz, now);
				}
			} else agent.blockedMovementTicks = 0;
			agent.remainingX -= dx;
			agent.remainingZ -= dz;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			agent.player.setYRot(yaw);
			agent.player.setYHeadRot(yaw);
		}
	}

	private void startAutomaticDecision(MinecraftServer server, Agent agent) {
		if (!config.get().hasApiKey()) {
			agent.lastMessage = "自动决策已开启，但服务器尚未配置 AI API 令牌";
			return;
		}
		if (!agent.thinking.compareAndSet(false, true)) return;
		if (!agent.activeTask.isBlank()) {
			requestDecision(server, agent,
				"继续执行尚未完成的持续任务；根据当前观察只选择下一小步，完成后返回 complete。任务："
					+ agent.activeTask,
				message -> {
					if (message.startsWith("AI 请求失败")) {
						AiCompanionMod.LOGGER.warn("Automatic task failed for {}: {}", agent.name, message);
					}
				});
			return;
		}
		String instruction = switch (agent.mode) {
			case SURVIVAL -> "像普通生存玩家一样，根据当前环境自主决定下一步非作弊行动。";
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
			case SURVIVAL -> PromptTemplates.applyTargets(prompts.get("survival"), "");
			case HUNTER -> PromptTemplates.applyTargets(prompts.get("hunter"), agent.targetName);
			case TEAMMATE -> PromptTemplates.applyTargets(prompts.get("teammate"), agent.targetName);
			case PVP_COACH -> PromptTemplates.applyTargets(prompts.get("pvp_coach"), agent.targetName);
			case IDLE -> PromptTemplates.applyTargets(prompts.get("idle"), agent.targetName);
		};
		return promptDecorator.apply(agent.name, prompt);
	}

	private Agent requireAgent(String name) {
		pruneStaleAgents();
		Agent agent = agents.get(name.toLowerCase());
		if (agent == null) throw new IllegalArgumentException("找不到 AI: " + name);
		return agent;
	}

	private void apply(MinecraftServer server, Agent agent, AiDecision decision) {
		BiConsumer<String, AiDecision> observer;
		synchronized (this) {
			agent.lastMessage = decision.say().isBlank()
				? "已执行动作：" + decision.action() : decision.say();
			switch (decision.action()) {
				case "move" -> {
					if (!agent.furnitureSeated) {
						agent.remainingX = decision.dx();
						agent.remainingZ = decision.dz();
					}
				}
				case "attack" -> attackAuthorizedTarget(server, agent);
				case "mine" -> tryMineObstacle(agent, decision.dx(), decision.dz(), server.getTickCount());
				case "complete" -> {
					agent.activeTask = "";
					agent.lastMessage = decision.say().isBlank() ? "任务已完成" : decision.say();
				}
				default -> { }
			}
			observer = actionObserver;
		}
		// Do not call feature modules while holding the agent lock: maid operations call back into us.
		observer.accept(agent.name, decision);
	}

	private void tickBuiltInBehaviour(MinecraftServer server, Agent agent, long now) {
		if (agent.mode == AgentMode.IDLE) return;
		// A direct task owns movement until the planner marks it complete. Without this guard the
		// built-in follow/wander controller overwrote every movement chosen from the assigned prompt.
		if (!agent.activeTask.isBlank()) return;
		if (agent.mode == AgentMode.SURVIVAL) {
			Mob threat = nearestHostile(agent, 12.0);
			if (threat != null) {
				double dx = threat.getX() - agent.player.getX();
				double dz = threat.getZ() - agent.player.getZ();
				double distance = Math.hypot(dx, dz);
				if (distance > 2.4) {
					agent.remainingX = dx / Math.max(0.001, distance) * Math.min(3.0, distance - 2.2);
					agent.remainingZ = dz / Math.max(0.001, distance) * Math.min(3.0, distance - 2.2);
				} else if (now - agent.lastAttackTick >= 12) {
					agent.player.attack(threat);
					agent.player.swing(InteractionHand.MAIN_HAND);
					agent.lastAttackTick = now;
					agent.lastMessage = "生存防卫：正在攻击 " + threat.getName().getString();
				}
			} else if (agent.activeTask.isBlank() && !agent.thinking.get()
					&& Math.hypot(agent.remainingX, agent.remainingZ) < 0.05 && now >= agent.nextWanderTick) {
				double angle = agent.player.getRandom().nextDouble() * Math.PI * 2.0;
				double distance = 2.0 + agent.player.getRandom().nextDouble() * 4.0;
				agent.remainingX = Math.cos(angle) * distance;
				agent.remainingZ = Math.sin(angle) * distance;
				agent.nextWanderTick = now + 50 + agent.player.getRandom().nextInt(90);
				agent.lastMessage = "生存巡查：正在观察附近环境";
			}
		} else {
			ServerPlayer target = server.getPlayerList().getPlayerByName(agent.targetName);
			if (target != null && target.level() == agent.player.level() && target.isAlive()) {
				double dx = target.getX() - agent.player.getX();
				double dz = target.getZ() - agent.player.getZ();
				double distance = Math.hypot(dx, dz);
				double desired = agent.mode == AgentMode.TEAMMATE ? 3.0 : 2.2;
				if (distance > desired) {
					double travel = Math.min(4.0, distance - desired);
					agent.remainingX = dx / distance * travel;
					agent.remainingZ = dz / distance * travel;
				}
				boolean mayAttack = agent.mode == AgentMode.HUNTER
					|| (agent.mode == AgentMode.PVP_COACH && target.getHealth() > 6.0F);
				if (mayAttack && distance <= 3.0 && now - agent.lastAttackTick >= 12) {
					agent.player.attack(target);
					agent.player.swing(InteractionHand.MAIN_HAND);
					agent.lastAttackTick = now;
					agent.lastMessage = agent.mode == AgentMode.HUNTER
						? "正在追击目标 " + target.getScoreboardName()
						: "正在与 " + target.getScoreboardName() + " 进行安全 PvP 训练";
				}
			}
		}
		if (now % 20L == 0L && agent.player.level() instanceof ServerLevel level) {
			AABB area = agent.player.getBoundingBox().inflate(16.0);
			level.getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy && mob.isAlive()
				&& mob.getTarget() == null).stream()
				.min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(agent.player)))
				.ifPresent(mob -> mob.setTarget(agent.player));
		}
	}

	private Mob nearestHostile(Agent agent, double radius) {
		if (!(agent.player.level() instanceof ServerLevel level)) return null;
		return level.getEntitiesOfClass(Mob.class, agent.player.getBoundingBox().inflate(radius),
			mob -> mob instanceof Enemy && mob.isAlive()).stream()
			.min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(agent.player))).orElse(null);
	}

	private void attackAuthorizedTarget(MinecraftServer server, Agent agent) {
		if (agent.mode == AgentMode.HUNTER || agent.mode == AgentMode.PVP_COACH) {
			ServerPlayer target = server.getPlayerList().getPlayerByName(agent.targetName);
			if (target != null && target.level() == agent.player.level()
					&& target.isAlive() && agent.player.distanceToSqr(target) <= 16.0) {
				agent.player.attack(target);
				agent.player.swing(InteractionHand.MAIN_HAND);
				agent.lastMessage = "正在攻击模式授权目标 " + target.getScoreboardName();
				return;
			}
		}
		Mob hostile = nearestHostile(agent, 4.0);
		if (hostile == null) {
			agent.lastMessage = "攻击动作未执行：近身范围没有获准目标";
			return;
		}
		agent.player.attack(hostile);
		agent.player.swing(InteractionHand.MAIN_HAND);
		agent.lastMessage = "正在防卫，攻击 " + hostile.getName().getString();
	}

	private String nearbyObservation(Agent agent) {
		if (!(agent.player.level() instanceof ServerLevel level)) return "附近实体=不可用";
		String entities = level.getEntitiesOfClass(LivingEntity.class,
			agent.player.getBoundingBox().inflate(12.0), entity -> entity != agent.player && entity.isAlive())
			.stream().sorted(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(agent.player)))
			.limit(8).map(entity -> "%s(%.1f格%s)".formatted(entity.getName().getString(),
				Math.sqrt(entity.distanceToSqr(agent.player)), entity instanceof Enemy ? ",敌对" : ""))
			.collect(java.util.stream.Collectors.joining("、"));
		BlockPos feet = agent.player.blockPosition();
		String below = level.getBlockState(feet.below()).getBlock().getName().getString();
		return "脚下方块=" + below + "，附近实体=" + (entities.isBlank() ? "无" : entities);
	}

	private void tryMineObstacle(Agent agent, double dx, double dz, long now) {
		if (!(agent.player.level() instanceof ServerLevel level)) return;
		int stepX = Math.abs(dx) < 0.001 ? 0 : dx > 0 ? 1 : -1;
		int stepZ = Math.abs(dz) < 0.001 ? 0 : dz > 0 ? 1 : -1;
		for (int yOffset = 0; yOffset <= 1; yOffset++) {
			BlockPos position = agent.player.blockPosition().offset(stepX, yOffset, stepZ);
			var state = level.getBlockState(position);
			float hardness = state.getDestroySpeed(level, position);
			if (state.isAir() || hardness < 0.0F || hardness > 3.0F
					|| level.getBlockEntity(position) != null) continue;
			if (level.destroyBlock(position, true, agent.player)) {
				agent.lastMessage = "正在清理移动路线上的 " + state.getBlock().getName().getString();
				agent.nextMineTick = now + 20;
				agent.blockedMovementTicks = 0;
				return;
			}
		}
		agent.nextMineTick = now + 10;
	}

	private static boolean requiresTarget(AgentMode mode) {
		return mode == AgentMode.HUNTER || mode == AgentMode.TEAMMATE || mode == AgentMode.PVP_COACH;
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private static String bounded(String value, int maximum) {
		String safe = value == null ? "" : value.strip();
		return safe.length() <= maximum ? safe : safe.substring(0, maximum);
	}

	private void bindStore(MinecraftServer server) {
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		if (boundWorldRoot != null && !boundWorldRoot.equals(worldRoot)) {
			if (!agents.isEmpty()) throw new IllegalStateException("Cannot reuse AI runtime state across worlds");
			identityStore = null;
		}
		if (identityStore == null) {
			boundWorldRoot = worldRoot;
			identityStore = AgentIdentityStore.loadForWorld(worldRoot);
		}
	}

	private boolean isLiveAgent(Agent agent) {
		MinecraftServer server = agent.player.level().getServer();
		if (server == null || agent.player.isRemoved()) return false;
		if (server.getPlayerList().getPlayer(agent.player.getUUID()) != agent.player) return false;
		return agent.player.level() instanceof ServerLevel level
			&& level.getEntity(agent.player.getUUID()) == agent.player;
	}

	private void pruneStaleAgents() {
		boolean removed = agents.entrySet().removeIf(entry -> !isLiveAgent(entry.getValue()));
		if (removed) {
			AiCompanionMod.LOGGER.warn("Removed stale AI runtime identities that no longer have live entities");
			saveIdentities();
		}
	}

	private void pruneFinishedDeaths() {
		boolean removed = false;
		var iterator = agents.entrySet().iterator();
		while (iterator.hasNext()) {
			Agent agent = iterator.next().getValue();
			if (agent.player.isAlive() || agent.player.deathTime < 20) continue;
			MinecraftServer server = agent.player.level().getServer();
			if (server != null && server.getPlayerList().getPlayer(agent.player.getUUID()) == agent.player) {
				server.getPlayerList().remove(agent.player);
			} else agent.player.discard();
			iterator.remove();
			removed = true;
			AiCompanionMod.LOGGER.info("Removed dead AI entity {}; the same name may now be created again",
				agent.name);
		}
		if (removed) saveIdentities();
	}

	private synchronized void saveIdentities() {
		if (identityStore == null) return;
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
		identityStore = null;
		boundWorldRoot = null;
		nextIdentitySaveTick = 0;
	}
}
