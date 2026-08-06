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

/** Minecraft 1.20.1 implementation of bounded persistent weather events. */
final class LegacyWeatherManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private final SplittableRandom random = new SplittableRandom();
	private State active;
	private long ticks;

	LegacyWeatherManager(Path file) { this.file = file; load(); }

	synchronized State start(String rawType, int minutes, boolean automatic) {
		Type type = Type.parse(rawType);
		if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("时长必须为 1～30 分钟");
		long duration = minutes * 60L * 20L;
		active = new State(type.name(), duration, duration, automatic); save(); return active;
	}

	synchronized boolean stop() { boolean existed = active != null; active = null; save(); return existed; }
	synchronized State active() { return active; }

	void tick(MinecraftServer server) {
		ticks++;
		State event;
		synchronized (this) { event = active; }
		if (event == null) { automatic(server); return; }
		Type type = Type.valueOf(event.type);
		if (type.nightOnly && !isNight(server.overworld())) { stop(); broadcast(server, type.label + "随日出结束"); return; }
		if (ticks % 5 == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) apply(player, type);
		synchronized (this) {
			if (active == null) return;
			active.remainingTicks--;
			if (active.remainingTicks <= 0) { active = null; broadcast(server, "自然事件已经结束"); }
			if (ticks % 200 == 0 || active == null) save();
		}
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
					ItemEntity shard = new ItemEntity(level, x, player.getY() + 8, z, new ItemStack(Items.AMETHYST_SHARD));
					shard.setDeltaMovement(0, -.18, 0); level.addFreshEntity(shard);
					player.sendSystemMessage(Component.literal("一枚星辰碎片坠落在附近（以紫水晶碎片承载）"));
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
		if (ticks % 1200 != 0 || random.nextInt(240) != 0) return;
		Type[] types = Type.values();
		Type type = isNight(server.overworld()) ? types[random.nextInt(types.length)]
			: (random.nextBoolean() ? Type.SANDSTORM : Type.ENHANCED_THUNDERSTORM);
		start(type.name(), 5 + random.nextInt(6), true); broadcast(server, "自然事件开始：" + type.label);
	}

	static Type parse(String value) { return Type.parse(value); }
	private static boolean isNight(ServerLevel level) { long time = Math.floorMod(level.getDayTime(), 24000L); return time >= 13000 && time <= 23000; }
	private static void broadcast(MinecraftServer server, String text) { server.getPlayerList().broadcastSystemMessage(Component.literal("[自然事件] " + text), false); }

	private synchronized void load() {
		if (!Files.isRegularFile(file)) return;
		try { State loaded = GSON.fromJson(Files.readString(file), State.class); if (loaded != null && loaded.valid()) active = loaded; }
		catch (Exception ignored) { active = null; }
	}

	private synchronized void save() {
		try {
			Files.createDirectories(file.getParent()); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(active), StandardCharsets.UTF_8);
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
}
