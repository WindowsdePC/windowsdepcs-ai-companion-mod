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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.SplittableRandom;

/** Runs one bounded natural event at a time and persists its remaining duration. */
public final class WeatherEventManager implements AutoCloseable {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long MAX_TICKS = 20L * 60L * 30L;
	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("ai_companion-weather.json");
	private final SplittableRandom random = new SplittableRandom();
	private ActiveWeatherEvent active;
	private long ticks;

	public WeatherEventManager() { load(); }

	public synchronized ActiveWeatherEvent start(WeatherEventType type, int minutes, boolean automatic) {
		if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("时长必须为 1～30 分钟");
		long duration = Math.min(MAX_TICKS, minutes * 60L * 20L);
		active = new ActiveWeatherEvent(type, duration, duration, automatic);
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

	public void tick(MinecraftServer server) {
		ticks++;
		ActiveWeatherEvent event;
		synchronized (this) { event = active; }
		if (event == null) {
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
			if (active.expired()) { active = null; broadcast(server, "自然事件已经结束"); }
			if (ticks % 200 == 0 || active == null) saveQuietly();
		}
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
			ItemEntity shard = new ItemEntity(level, x, player.getY() + 8, z, new ItemStack(Items.AMETHYST_SHARD));
			shard.setDeltaMovement(0, -0.18, 0); level.addFreshEntity(shard);
			player.sendSystemMessage(Component.literal("一枚星辰碎片坠落在附近（以紫水晶碎片承载）"));
		}
	}

	private void sandstorm(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getEyeY(), player.getZ(), 12, 5, 2, 5, 0.05);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 0, false, false));
	}

	private void thunder(ServerPlayer player) {
		ServerLevel level = player.level();
		if (ticks % 200 == 0) level.setWeatherParameters(0, 20 * 30, true, true);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 8, player.getZ(), 3, 8, 4, 8, 0.02);
	}

	private void tryAutomaticStart(MinecraftServer server) {
		if (ticks % 1200 != 0 || random.nextInt(240) != 0) return;
		WeatherEventType type = isNight(server.overworld())
			? WeatherEventType.values()[random.nextInt(WeatherEventType.values().length)]
			: (random.nextBoolean() ? WeatherEventType.SANDSTORM : WeatherEventType.ENHANCED_THUNDERSTORM);
		start(type, 5 + random.nextInt(6), true);
		broadcast(server, "自然事件开始：" + type.displayName());
	}

	private static boolean isNight(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		return time >= 13000L && time <= 23000L;
	}

	private synchronized void finish(MinecraftServer server, String message) { active = null; saveQuietly(); broadcast(server, message); }
	private static void broadcast(MinecraftServer server, String message) { server.getPlayerList().broadcastSystemMessage(Component.literal("[自然事件] " + message), false); }

	private synchronized void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			Stored stored = GSON.fromJson(Files.readString(file), Stored.class);
			if (stored != null && stored.type != null && stored.remainingTicks > 0) {
				long total = Math.max(20, Math.min(MAX_TICKS, stored.totalTicks));
				active = new ActiveWeatherEvent(WeatherEventType.valueOf(stored.type.toUpperCase(Locale.ROOT)), stored.remainingTicks, total, stored.automatic);
			}
		} catch (Exception ignored) { active = null; }
	}

	private synchronized void saveQuietly() {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Stored stored = active == null ? new Stored() : new Stored(active);
			Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
			try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (IOException atomicFailure) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException ignored) { }
	}

	@Override public synchronized void close() { saveQuietly(); }
	private static final class Stored {
		private String type; private long remainingTicks, totalTicks; private boolean automatic;
		private Stored() { }
		private Stored(ActiveWeatherEvent event) { type = event.type().name(); remainingTicks = event.remainingTicks(); totalTicks = event.totalTicks(); automatic = event.automatic(); }
	}
}
