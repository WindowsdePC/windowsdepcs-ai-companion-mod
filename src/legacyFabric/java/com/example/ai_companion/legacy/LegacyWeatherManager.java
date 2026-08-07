package com.example.ai_companion.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/** Minecraft 1.20.1 implementation of bounded persistent weather events. */
final class LegacyWeatherManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_HISTORY = 32;
	private static final int MAX_SCHEDULES = 32;
	private final Path file;
	private final SplittableRandom random = new SplittableRandom();
	private final List<History> history = new ArrayList<>();
	private final List<Scheduled> schedules = new ArrayList<>();
	private final Set<UUID> unsubscribed = new HashSet<>();
	private final EnumMap<Type, Integer> typeWeights = defaultTypeWeights();
	private Policy policy = Policy.defaults();
	private int automaticCooldownMinutes = 30;
	private State active;
	private int nextScheduleId = 1;
	private long ticks;

	LegacyWeatherManager(Path file) { this.file = file; load(); }

	synchronized State start(String rawType, int minutes, boolean automatic) {
		Type type = Type.parse(rawType);
		if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("时长必须为 1～30 分钟");
		long duration = minutes * 60L * 20L;
		active = new State(type.name(), duration, duration, automatic);
		history.add(0, new History(type.name(), System.currentTimeMillis(), minutes * 60, automatic));
		while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
		save();
		return active;
	}

	synchronized boolean stop() { boolean existed = active != null; active = null; save(); return existed; }
	synchronized State active() { return active; }
	synchronized Policy policy() { return policy.copy(); }
	synchronized List<History> history(int limit) { return List.copyOf(history.subList(0, Math.min(Math.max(1, limit), history.size()))); }
	synchronized Scheduled schedule(String rawType, int delayMinutes, int durationMinutes) {
		if (schedules.size() >= MAX_SCHEDULES) throw new IllegalStateException("最多只能保存 32 个自然事件日程");
		if (delayMinutes < 1 || delayMinutes > 10080) throw new IllegalArgumentException("延迟必须为 1～10080 分钟");
		Scheduled event = new Scheduled(nextScheduleId++, Type.parse(rawType).name(),
			Math.addExact(System.currentTimeMillis(), delayMinutes * 60_000L), durationMinutes);
		schedules.add(event); schedules.sort(null); save(); return event;
	}
	synchronized List<Scheduled> schedules() { return List.copyOf(schedules); }
	synchronized boolean cancelSchedule(int id) { boolean removed = schedules.removeIf(event -> event.id == id); if (removed) save(); return removed; }
	synchronized Summary statistics(Type filter) {
		int events = 0, automatic = 0; long seconds = 0;
		for (History entry : history) {
			if (filter != null && !entry.type.equals(filter.name())) continue;
			events++; if (entry.automatic) automatic++; seconds += entry.plannedDurationSeconds;
		}
		return new Summary(events, automatic, events - automatic, seconds);
	}
	synchronized int typeWeight(Type type) { return typeWeights.getOrDefault(type, 100); }
	synchronized int automaticCooldownMinutes() { return automaticCooldownMinutes; }
	synchronized void setAutomaticCooldownMinutes(int minutes) {
		if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("自动事件冷却必须为 0～1440 分钟");
		automaticCooldownMinutes = minutes; save();
	}
	synchronized int automaticCooldownRemainingSeconds() {
		if (automaticCooldownMinutes <= 0) return 0;
		for (History entry : history) if (entry.automatic) {
			long remaining = entry.startedAtEpochMillis + automaticCooldownMinutes * 60_000L - System.currentTimeMillis();
			return remaining <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (remaining + 999L) / 1000L);
		}
		return 0;
	}
	synchronized void setTypeWeight(Type type, int weight) {
		if (weight < 0 || weight > 1000) throw new IllegalArgumentException("事件权重必须为 0～1000");
		typeWeights.put(type, weight); save();
	}
	synchronized String eligibleLabels(boolean night) {
		StringBuilder value = new StringBuilder();
		for (Type type : Type.values()) if ((night || !type.nightOnly) && typeWeight(type) > 0) {
			if (!value.isEmpty()) value.append('/'); value.append(type.label).append('(').append(typeWeight(type)).append(')');
		}
		return value.isEmpty() ? "无" : value.toString();
	}
	synchronized String weightSummary() {
		StringBuilder value = new StringBuilder();
		for (Type type : Type.values()) { if (!value.isEmpty()) value.append('，'); value.append(type.label).append('=').append(typeWeight(type)); }
		return value.toString();
	}
	synchronized int nextAutomaticCheckSeconds() {
		long interval = policy.checkIntervalSeconds * 20L;
		return (int) Math.ceil((interval - Math.floorMod(ticks, interval)) / 20.0);
	}
	synchronized void setAutomaticEnabled(boolean value) { policy.automaticEnabled = value; save(); }
	synchronized void setCheckInterval(int value) { Policy.validate(value, policy.chanceDenominator, policy.minDurationMinutes, policy.maxDurationMinutes); policy.checkIntervalSeconds = value; save(); }
	synchronized void setChance(int value) { Policy.validate(policy.checkIntervalSeconds, value, policy.minDurationMinutes, policy.maxDurationMinutes); policy.chanceDenominator = value; save(); }
	synchronized void setDuration(int minimum, int maximum) { Policy.validate(policy.checkIntervalSeconds, policy.chanceDenominator, minimum, maximum); policy.minDurationMinutes = minimum; policy.maxDurationMinutes = maximum; save(); }
	synchronized void setNotifications(UUID playerId, boolean enabled) { if (enabled) unsubscribed.remove(playerId); else unsubscribed.add(playerId); save(); }
	synchronized boolean notificationsEnabled(UUID playerId) { return !unsubscribed.contains(playerId); }

	void tick(MinecraftServer server) {
		ticks++;
		State event;
		synchronized (this) { event = active; }
		if (event == null) { if (!scheduled(server)) automatic(server); return; }
		Type type = Type.valueOf(event.type);
		if (type.nightOnly && !isNight(server.overworld())) { stop(); announce(server, type.label + "随日出结束"); return; }
		if (ticks % 5 == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) apply(player, type);
		synchronized (this) {
			if (active == null) return;
			active.remainingTicks--;
			if (active.remainingTicks <= 0) { active = null; announce(server, "自然事件已经结束"); }
			if (ticks % 200 == 0 || active == null) save();
		}
	}

	private boolean scheduled(MinecraftServer server) {
		long now = System.currentTimeMillis(); boolean night = isNight(server.overworld()); Scheduled due;
		synchronized (this) {
			due = schedules.stream().filter(event -> event.due(now) && event.eligible(night)).findFirst().orElse(null);
			if (due == null) return false; schedules.remove(due);
		}
		start(due.type, due.durationMinutes, false); announce(server, "预约事件开始：" + Type.valueOf(due.type).label + "（日程 #" + due.id + "）"); return true;
	}

	private void apply(ServerPlayer player, Type type) {
		if (player.level().dimension() != Level.OVERWORLD) return;
		ServerLevel level = player.serverLevel();
		double x = player.getX() + random.nextDouble(-20, 20), z = player.getZ() + random.nextDouble(-20, 20);
		switch (type) {
			case AURORA -> {
				level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 18, z, 2, 6, 1, 6, 0.01);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, player.getY() + 16, z, 2, 5, .5, 5, .01);
			}
			case METEOR_SHOWER -> {
				level.sendParticles(ParticleTypes.FIREWORK, x, player.getY() + 24, z, 2, .3, 4, .3, .18);
				if (ticks % 200 == 0 && random.nextInt(4) == 0) {
					ItemEntity shard = new ItemEntity(level, x, player.getY() + 8, z, new ItemStack(LegacyWeatherItems.STAR_SHARD));
					shard.setDeltaMovement(0, -.18, 0); level.addFreshEntity(shard);
					player.sendSystemMessage(Component.literal("一枚星辰碎片坠落在附近"));
				}
			}
			case SANDSTORM -> {
				if (!level.getBiome(player.blockPosition()).is(Biomes.DESERT)) return;
				level.sendParticles(ParticleTypes.POOF, player.getX(), player.getEyeY(), player.getZ(), 12, 5, 2, 5, .05);
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 0, false, false));
			}
			case ENHANCED_THUNDERSTORM -> {
				if (ticks % 200 == 0) level.setWeatherParameters(0, 20 * 30, true, true);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 8, player.getZ(), 3, 8, 4, 8, .02);
			}
		}
	}

	private void automatic(MinecraftServer server) {
		Policy current;
		synchronized (this) { current = policy.copy(); }
		long interval = current.checkIntervalSeconds * 20L;
		if (!current.automaticEnabled || ticks % interval != 0 || automaticCooldownRemainingSeconds() > 0
			|| random.nextInt(current.chanceDenominator) != 0) return;
		Type type = chooseAutomaticType(isNight(server.overworld()));
		if (type == null) return;
		int minutes = current.minDurationMinutes + random.nextInt(current.maxDurationMinutes - current.minDurationMinutes + 1);
		start(type.name(), minutes, true); announce(server, "自然事件开始：" + type.label);
	}

	private synchronized Type chooseAutomaticType(boolean night) {
		List<Type> candidates = new ArrayList<>();
		for (Type type : Type.values()) if ((night || !type.nightOnly) && typeWeight(type) > 0) candidates.add(type);
		Type previous = null;
		for (History entry : history) if (entry.automatic) { previous = Type.valueOf(entry.type); break; }
		if (previous != null && candidates.size() > 1) candidates.remove(previous);
		int total = 0;
		for (Type type : candidates) total += typeWeight(type);
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (Type type : candidates) {
			int weight = typeWeight(type);
			if (roll < weight) return type; roll -= weight;
		}
		return null;
	}

	static Type parse(String value) { return Type.parse(value); }
	private static boolean isNight(ServerLevel level) { long time = Math.floorMod(level.getDayTime(), 24000L); return time >= 13000 && time <= 23000; }
	void announce(MinecraftServer server, String text) {
		for (ServerPlayer player : server.getPlayerList().getPlayers())
			if (notificationsEnabled(player.getUUID())) player.sendSystemMessage(Component.literal("[自然事件] " + text));
	}

	private synchronized void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			Store loaded = GSON.fromJson(Files.readString(file), Store.class);
			if (loaded == null) return;
			State candidate = loaded.active != null ? loaded.active : loaded.legacyState();
			if (candidate != null && candidate.valid()) active = candidate;
			if (loaded.policy != null && loaded.policy.valid()) policy = loaded.policy;
			automaticCooldownMinutes = loaded.automaticCooldownMinutes == null ? 30 : Math.max(0, Math.min(1440, loaded.automaticCooldownMinutes));
			typeWeights.clear(); typeWeights.putAll(defaultTypeWeights());
			if (loaded.typeWeights != null) for (Map.Entry<String, Integer> entry : loaded.typeWeights.entrySet()) try {
				Type type = Type.valueOf(entry.getKey()); int weight = entry.getValue(); if (weight >= 0 && weight <= 1000) typeWeights.put(type, weight);
			} catch (Exception ignored) { }
			if (loaded.history != null) loaded.history.stream().filter(History::valid).limit(MAX_HISTORY).forEach(history::add);
			if (loaded.schedules != null) loaded.schedules.stream().filter(event -> event != null && event.valid()).sorted().limit(MAX_SCHEDULES).forEach(schedules::add);
			nextScheduleId = Math.max(1, schedules.stream().mapToInt(event -> event.id).max().orElse(0) + 1);
			if (loaded.unsubscribed != null) for (String value : loaded.unsubscribed) try { unsubscribed.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
		} catch (Exception ignored) { active = null; policy = Policy.defaults(); automaticCooldownMinutes = 30; history.clear(); schedules.clear(); nextScheduleId = 1; unsubscribed.clear(); typeWeights.clear(); typeWeights.putAll(defaultTypeWeights()); }
	}

	private synchronized void save() {
		try {
			Files.createDirectories(file.getParent()); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(new Store(this)), StandardCharsets.UTF_8);
			try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (IOException failure) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException ignored) { }
	}

	enum Type {
		AURORA("极光", true), METEOR_SHOWER("流星雨", true), SANDSTORM("沙尘暴", false), ENHANCED_THUNDERSTORM("增强雷暴", false);
		final String label; final boolean nightOnly;
		Type(String label, boolean nightOnly) { this.label = label; this.nightOnly = nightOnly; }
		static Type parse(String raw) {
			String value = raw.toLowerCase(Locale.ROOT).replace('-', '_');
			return switch (value) {
				case "aurora" -> AURORA; case "meteor", "meteor_shower" -> METEOR_SHOWER;
				case "sandstorm" -> SANDSTORM; case "thunder", "enhanced_thunderstorm" -> ENHANCED_THUNDERSTORM;
				default -> throw new IllegalArgumentException("事件必须是 aurora、meteor、sandstorm 或 thunder");
			};
		}
	}

	static final class State {
		String type; long remainingTicks, totalTicks; boolean automatic;
		State(String type, long remainingTicks, long totalTicks, boolean automatic) { this.type = type; this.remainingTicks = remainingTicks; this.totalTicks = totalTicks; this.automatic = automatic; }
		boolean valid() { try { Type.valueOf(type); return remainingTicks > 0 && remainingTicks <= totalTicks && totalTicks <= 20L * 60 * 30; } catch (Exception error) { return false; } }
		int remainingSeconds() { return (int) Math.ceil(remainingTicks / 20.0); }
	}

	static final class Policy {
		boolean automaticEnabled = true; int checkIntervalSeconds = 60, chanceDenominator = 240, minDurationMinutes = 5, maxDurationMinutes = 10;
		static Policy defaults() { return new Policy(); }
		Policy copy() { Policy result = new Policy(); result.automaticEnabled = automaticEnabled; result.checkIntervalSeconds = checkIntervalSeconds; result.chanceDenominator = chanceDenominator; result.minDurationMinutes = minDurationMinutes; result.maxDurationMinutes = maxDurationMinutes; return result; }
		boolean valid() { try { validate(checkIntervalSeconds, chanceDenominator, minDurationMinutes, maxDurationMinutes); return true; } catch (IllegalArgumentException error) { return false; } }
		static void validate(int interval, int chance, int minimum, int maximum) {
			if (interval < 30 || interval > 3600) throw new IllegalArgumentException("检查间隔必须为 30～3600 秒");
			if (chance < 1 || chance > 10000) throw new IllegalArgumentException("概率分母必须为 1～10000");
			if (minimum < 1 || maximum > 30 || minimum > maximum) throw new IllegalArgumentException("时长必须满足 1 ≤ 最短 ≤ 最长 ≤ 30");
		}
	}

	static final class History {
		String type; long startedAtEpochMillis; int plannedDurationSeconds; boolean automatic;
		History(String type, long startedAtEpochMillis, int plannedDurationSeconds, boolean automatic) { this.type = type; this.startedAtEpochMillis = startedAtEpochMillis; this.plannedDurationSeconds = plannedDurationSeconds; this.automatic = automatic; }
		boolean valid() { try { Type.valueOf(type); return startedAtEpochMillis >= 0 && plannedDurationSeconds >= 60 && plannedDurationSeconds <= 1800; } catch (Exception error) { return false; } }
	}

	static final class Scheduled implements Comparable<Scheduled> {
		int id; String type; long scheduledAtEpochMillis; int durationMinutes;
		Scheduled(int id, String type, long scheduledAtEpochMillis, int durationMinutes) {
			this.id = id; this.type = type; this.scheduledAtEpochMillis = scheduledAtEpochMillis; this.durationMinutes = durationMinutes;
			if (!valid()) throw new IllegalArgumentException("自然事件日程无效");
		}
		boolean valid() { try { Type.valueOf(type); return id > 0 && scheduledAtEpochMillis > 0 && durationMinutes >= 1 && durationMinutes <= 30; } catch (Exception error) { return false; } }
		boolean due(long now) { return now >= scheduledAtEpochMillis; }
		boolean eligible(boolean night) { return night || !Type.valueOf(type).nightOnly; }
		@Override public int compareTo(Scheduled other) { int time = Long.compare(scheduledAtEpochMillis, other.scheduledAtEpochMillis); return time != 0 ? time : Integer.compare(id, other.id); }
	}

	static final class Summary {
		final int events, automaticEvents, administratorEvents; final long plannedDurationSeconds;
		Summary(int events, int automaticEvents, int administratorEvents, long plannedDurationSeconds) {
			this.events = events; this.automaticEvents = automaticEvents; this.administratorEvents = administratorEvents;
			this.plannedDurationSeconds = plannedDurationSeconds;
		}
	}

	private static EnumMap<Type, Integer> defaultTypeWeights() {
		EnumMap<Type, Integer> values = new EnumMap<>(Type.class); for (Type type : Type.values()) values.put(type, 100); return values;
	}

	private static final class Store {
		State active; Policy policy; List<History> history; List<Scheduled> schedules; List<String> unsubscribed; Map<String, Integer> typeWeights; Integer automaticCooldownMinutes;
		String type; long remainingTicks, totalTicks; boolean automatic;
		Store() { }
		Store(LegacyWeatherManager manager) {
			active = manager.active; policy = manager.policy.copy(); history = List.copyOf(manager.history);
			schedules = List.copyOf(manager.schedules);
			automaticCooldownMinutes = manager.automaticCooldownMinutes;
			unsubscribed = manager.unsubscribed.stream().map(UUID::toString).sorted().toList();
			typeWeights = new java.util.LinkedHashMap<>(); for (Type type : Type.values()) typeWeights.put(type.name(), manager.typeWeight(type));
		}
		State legacyState() { return type == null ? null : new State(type, remainingTicks, totalTicks, automatic); }
	}
}
