package com.example.ai_companion.weather;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.saveddata.WeatherData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/** Runs one bounded natural event at a time and persists state, policy, history and subscriptions. */
public final class WeatherEventManager implements AutoCloseable {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long MAX_TICKS = 20L * 60L * 30L;
	private static final int MAX_HISTORY = 32;
	private static final int MAX_SCHEDULES = 32;
	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("ai_companion-weather.json");
	private final SplittableRandom random = new SplittableRandom();
	private final List<WeatherEventRecord> history = new ArrayList<>();
	private final List<ScheduledWeatherEvent> schedules = new ArrayList<>();
	private final Set<UUID> unsubscribed = new HashSet<>();
	private final EnumMap<WeatherEventType, Integer> typeWeights = defaultTypeWeights();
	private WeatherEventSettings settings = WeatherEventSettings.defaults();
	private int automaticCooldownMinutes = 30;
	private ActiveWeatherEvent active;
	private int nextScheduleId = 1;
	private long ticks;

	public WeatherEventManager() { load(); }

	public synchronized ActiveWeatherEvent start(WeatherEventType type, int minutes, boolean automatic) {
		if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("时长必须为 1～30 分钟");
		long duration = Math.min(MAX_TICKS, minutes * 60L * 20L);
		active = new ActiveWeatherEvent(type, duration, duration, automatic);
		history.add(0, new WeatherEventRecord(type, System.currentTimeMillis(), minutes * 60, automatic));
		trimHistory();
		saveQuietly();
		return active;
	}

	public synchronized boolean stop() {
		boolean existed = active != null;
		active = null;
		saveQuietly();
		return existed;
	}

	public synchronized ActiveWeatherEvent active() { return active; }
	public synchronized WeatherEventSettings settings() { return settings; }
	public synchronized List<WeatherEventRecord> history(int limit) {
		return List.copyOf(history.subList(0, Math.min(Math.max(1, limit), history.size())));
	}
	public synchronized ScheduledWeatherEvent schedule(WeatherEventType type, int delayMinutes, int durationMinutes) {
		if (schedules.size() >= MAX_SCHEDULES) throw new IllegalStateException("最多只能保存 32 个自然事件日程");
		if (delayMinutes < 1 || delayMinutes > 10080) throw new IllegalArgumentException("延迟必须为 1～10080 分钟");
		long when = Math.addExact(System.currentTimeMillis(), delayMinutes * 60_000L);
		ScheduledWeatherEvent event = new ScheduledWeatherEvent(nextScheduleId++, type, when, durationMinutes);
		schedules.add(event); schedules.sort(null); saveQuietly(); return event;
	}
	public synchronized List<ScheduledWeatherEvent> schedules() { return List.copyOf(schedules); }
	public synchronized boolean cancelSchedule(int id) {
		boolean removed = schedules.removeIf(event -> event.id() == id);
		if (removed) saveQuietly(); return removed;
	}
	public synchronized void updateSettings(WeatherEventSettings value) {
		settings = value == null ? WeatherEventSettings.defaults() : value;
		saveQuietly();
	}
	public synchronized int typeWeight(WeatherEventType type) { return typeWeights.getOrDefault(type, 100); }
	public synchronized int automaticCooldownMinutes() { return automaticCooldownMinutes; }
	public synchronized void setAutomaticCooldownMinutes(int minutes) {
		if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("自动事件冷却必须为 0～1440 分钟");
		automaticCooldownMinutes = minutes; saveQuietly();
	}
	public synchronized int automaticCooldownRemainingSeconds() {
		return WeatherAutomaticPolicy.remainingCooldownSeconds(history, automaticCooldownMinutes, System.currentTimeMillis());
	}
	public synchronized void setTypeWeight(WeatherEventType type, int weight) {
		if (type == null) throw new IllegalArgumentException("事件类型不能为空");
		if (weight < 0 || weight > 1000) throw new IllegalArgumentException("事件权重必须为 0～1000");
		typeWeights.put(type, weight); saveQuietly();
	}
	public synchronized String eligibleTypeLabels(boolean night) {
		String labels = java.util.Arrays.stream(WeatherEventType.values())
			.filter(type -> (night || !type.nightOnly()) && typeWeight(type) > 0)
			.map(type -> type.displayName() + "(" + typeWeight(type) + ")")
			.collect(java.util.stream.Collectors.joining("/"));
		return labels.isBlank() ? "无" : labels;
	}
	public synchronized String typeWeightSummary() {
		return java.util.Arrays.stream(WeatherEventType.values())
			.map(type -> type.displayName() + "=" + typeWeight(type))
			.collect(java.util.stream.Collectors.joining("，"));
	}
	public synchronized boolean notificationsEnabled(UUID playerId) { return !unsubscribed.contains(playerId); }
	public synchronized boolean setNotifications(UUID playerId, boolean enabled) {
		boolean changed = enabled ? unsubscribed.remove(playerId) : unsubscribed.add(playerId);
		if (changed) saveQuietly();
		return enabled;
	}
	public synchronized int nextAutomaticCheckSeconds() {
		long interval = settings.checkIntervalSeconds() * 20L;
		long remaining = interval - Math.floorMod(ticks, interval);
		return (int) Math.ceil(remaining / 20.0);
	}

	public void tick(MinecraftServer server) {
		ticks++;
		ActiveWeatherEvent event;
		synchronized (this) { event = active; }
		if (event == null) {
			if (tryScheduledStart(server)) return;
			tryAutomaticStart(server);
			return;
		}
		if (event.type().nightOnly() && !isNight(server.overworld())) {
			finish(server, event.type().displayName() + "随日出结束");
			return;
		}
		if (ticks % 5 == 0) apply(server, event);
		synchronized (this) {
			if (active == null) return;
			active = active.nextTick();
			if (active.expired()) { active = null; announce(server, "自然事件已经结束"); }
			if (ticks % 200 == 0 || active == null) saveQuietly();
		}
	}

	private boolean tryScheduledStart(MinecraftServer server) {
		long now = System.currentTimeMillis();
		boolean night = isNight(server.overworld());
		ScheduledWeatherEvent due;
		synchronized (this) {
			due = schedules.stream().filter(event -> event.due(now) && event.eligible(night)).findFirst().orElse(null);
			if (due == null) return false;
			schedules.remove(due);
		}
		start(due.type(), due.durationMinutes(), false);
		announce(server, "预约事件开始：" + due.type().displayName() + "（日程 #" + due.id() + "）");
		return true;
	}

	private void apply(MinecraftServer server, ActiveWeatherEvent event) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Level.OVERWORLD) continue;
			switch (event.type()) {
				case AURORA -> aurora(player);
				case METEOR_SHOWER -> meteor(player);
				case SANDSTORM -> { if (player.level().getBiome(player.blockPosition()).is(Biomes.DESERT)) sandstorm(player); }
				case ENHANCED_THUNDERSTORM -> thunder(player);
			}
		}
	}

	private void aurora(ServerPlayer player) {
		ServerLevel level = player.level();
		double x = player.getX() + random.nextDouble(-20, 20);
		double z = player.getZ() + random.nextDouble(-20, 20);
		level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 18, z, 2, 6, 1, 6, 0.01);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, player.getY() + 16, z, 2, 5, 0.5, 5, 0.01);
	}

	private void meteor(ServerPlayer player) {
		ServerLevel level = player.level();
		double x = player.getX() + random.nextDouble(-24, 24);
		double z = player.getZ() + random.nextDouble(-24, 24);
		level.sendParticles(ParticleTypes.FIREWORK, x, player.getY() + 24, z, 2, 0.3, 4, 0.3, 0.18);
		if (ticks % 200 == 0 && random.nextInt(4) == 0) {
			ItemEntity shard = new ItemEntity(level, x, player.getY() + 8, z, new ItemStack(WeatherItems.STAR_SHARD));
			shard.setDeltaMovement(0, -0.18, 0); level.addFreshEntity(shard);
			player.sendSystemMessage(Component.literal("一枚星辰碎片坠落在附近"));
		}
	}

	private void sandstorm(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getEyeY(), player.getZ(), 12, 5, 2, 5, 0.05);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 0, false, false));
	}

	private void thunder(ServerPlayer player) {
		ServerLevel level = player.level();
		if (ticks % 200 == 0) {
			WeatherData weather = level.getWeatherData();
			weather.setClearWeatherTime(0);
			weather.setRainTime(20 * 30);
			weather.setThunderTime(20 * 30);
			weather.setRaining(true);
			weather.setThundering(true);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 8, player.getZ(), 3, 8, 4, 8, 0.02);
	}

	private void tryAutomaticStart(MinecraftServer server) {
		WeatherEventSettings policy;
		synchronized (this) { policy = settings; }
		long interval = policy.checkIntervalSeconds() * 20L;
		if (!policy.automaticEnabled() || ticks % interval != 0 || automaticCooldownRemainingSeconds() > 0
			|| random.nextInt(policy.chanceDenominator()) != 0) return;
		WeatherEventType type = chooseAutomaticType(isNight(server.overworld()));
		if (type == null) return;
		int minutes = policy.minDurationMinutes() + random.nextInt(policy.maxDurationMinutes() - policy.minDurationMinutes() + 1);
		start(type, minutes, true);
		announce(server, "自然事件开始：" + type.displayName());
	}

	private synchronized WeatherEventType chooseAutomaticType(boolean night) {
		List<WeatherEventType> candidates = new ArrayList<>();
		for (WeatherEventType type : WeatherEventType.values())
			if ((night || !type.nightOnly()) && typeWeight(type) > 0) candidates.add(type);
		candidates = WeatherAutomaticPolicy.avoidImmediateRepeat(candidates,
			WeatherAutomaticPolicy.mostRecentAutomaticType(history));
		int total = 0;
		for (WeatherEventType type : candidates) total += typeWeight(type);
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (WeatherEventType type : candidates) {
			int weight = typeWeight(type);
			if (roll < weight) return type;
			roll -= weight;
		}
		return null;
	}

	private static boolean isNight(ServerLevel level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 13000L && time <= 23000L;
	}

	private synchronized void finish(MinecraftServer server, String message) {
		active = null; saveQuietly(); announce(server, message);
	}

	public void announce(MinecraftServer server, String message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (notificationsEnabled(player.getUUID())) player.sendSystemMessage(Component.literal("[自然事件] " + message));
		}
	}

	private synchronized void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			Stored stored = GSON.fromJson(Files.readString(file), Stored.class);
			if (stored == null) return;
			if (stored.type != null && stored.remainingTicks > 0) {
				long total = Math.max(20, Math.min(MAX_TICKS, stored.totalTicks));
				active = new ActiveWeatherEvent(WeatherEventType.valueOf(stored.type.toUpperCase(Locale.ROOT)), stored.remainingTicks, total, stored.automatic);
			}
			settings = stored.settings == null ? WeatherEventSettings.defaults() : stored.settings;
			automaticCooldownMinutes = stored.automaticCooldownMinutes == null ? 30 : Math.max(0, Math.min(1440, stored.automaticCooldownMinutes));
			typeWeights.clear(); typeWeights.putAll(defaultTypeWeights());
			if (stored.typeWeights != null) for (Map.Entry<String, Integer> entry : stored.typeWeights.entrySet()) try {
				WeatherEventType type = WeatherEventType.valueOf(entry.getKey()); int weight = entry.getValue();
				if (weight >= 0 && weight <= 1000) typeWeights.put(type, weight);
			} catch (Exception ignored) { }
			if (stored.history != null) stored.history.stream().filter(record -> record != null).limit(MAX_HISTORY).forEach(history::add);
			if (stored.schedules != null) stored.schedules.stream().filter(event -> event != null).filter(event -> {
				try { new ScheduledWeatherEvent(event.id(), event.type(), event.scheduledAtEpochMillis(), event.durationMinutes()); return true; }
				catch (RuntimeException ignored) { return false; }
			}).sorted().limit(MAX_SCHEDULES).forEach(schedules::add);
			nextScheduleId = Math.max(1, schedules.stream().mapToInt(ScheduledWeatherEvent::id).max().orElse(0) + 1);
			if (stored.unsubscribed != null) for (String value : stored.unsubscribed) try { unsubscribed.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
		} catch (Exception ignored) { active = null; settings = WeatherEventSettings.defaults(); automaticCooldownMinutes = 30; history.clear(); schedules.clear(); nextScheduleId = 1; unsubscribed.clear(); typeWeights.clear(); typeWeights.putAll(defaultTypeWeights()); }
	}

	private synchronized void saveQuietly() {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(new Stored(this)), StandardCharsets.UTF_8);
			try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (IOException atomicFailure) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException ignored) { }
	}

	private void trimHistory() { while (history.size() > MAX_HISTORY) history.remove(history.size() - 1); }
	private static EnumMap<WeatherEventType, Integer> defaultTypeWeights() {
		EnumMap<WeatherEventType, Integer> values = new EnumMap<>(WeatherEventType.class);
		for (WeatherEventType type : WeatherEventType.values()) values.put(type, 100);
		return values;
	}
	@Override public synchronized void close() { saveQuietly(); }

	private static final class Stored {
		private String type;
		private long remainingTicks, totalTicks;
		private boolean automatic;
		private WeatherEventSettings settings;
		private Integer automaticCooldownMinutes;
		private Map<String, Integer> typeWeights;
		private List<WeatherEventRecord> history;
		private List<ScheduledWeatherEvent> schedules;
		private List<String> unsubscribed;
		private Stored() { }
		private Stored(WeatherEventManager manager) {
			if (manager.active != null) {
				type = manager.active.type().name(); remainingTicks = manager.active.remainingTicks();
				totalTicks = manager.active.totalTicks(); automatic = manager.active.automatic();
			}
			settings = manager.settings;
			automaticCooldownMinutes = manager.automaticCooldownMinutes;
			typeWeights = new java.util.LinkedHashMap<>();
			for (WeatherEventType eventType : WeatherEventType.values()) typeWeights.put(eventType.name(), manager.typeWeight(eventType));
			history = List.copyOf(manager.history);
			schedules = List.copyOf(manager.schedules);
			unsubscribed = manager.unsubscribed.stream().map(UUID::toString).sorted().toList();
		}
	}
}
