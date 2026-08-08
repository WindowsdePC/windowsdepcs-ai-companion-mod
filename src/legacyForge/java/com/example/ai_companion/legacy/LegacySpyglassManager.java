package com.example.ai_companion.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Minecraft 1.20.1 implementation using only old Mojang mappings. */
final class LegacySpyglassManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private final Map<String, Settings> saved = new HashMap<>();
	private final Map<UUID, Integer> useTicks = new HashMap<>();
	private final Map<UUID, Integer> cooldownTicks = new HashMap<>();
	private final Set<UUID> triggered = new HashSet<>();

	LegacySpyglassManager(Path file) { this.file = file; load(); }

	LiteralArgumentBuilder<CommandSourceStack> command() {
		return literal("spyglass")
			.then(literal("status").executes(c -> status(c.getSource().getPlayerOrException())))
			.then(literal("enabled").then(argument("value", BoolArgumentType.bool())
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withEnabled(BoolArgumentType.getBool(c, "value"))))))
			.then(literal("radius-chunks").then(argument("value", IntegerArgumentType.integer(1, 32))
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withRadius(IntegerArgumentType.getInteger(c, "value"))))))
			.then(literal("hold-seconds").then(argument("value", IntegerArgumentType.integer(1, 10))
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withHoldSeconds(IntegerArgumentType.getInteger(c, "value"))))))
			.then(literal("duration-seconds").then(argument("value", IntegerArgumentType.integer(1, 600))
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withDurationSeconds(IntegerArgumentType.getInteger(c, "value"))))))
			.then(literal("target").then(argument("value", StringArgumentType.word())
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withTarget(StringArgumentType.getString(c, "value"))))))
			.then(literal("cooldown-seconds").then(argument("value", IntegerArgumentType.integer(1, 600))
				.executes(c -> update(c.getSource().getPlayerOrException(), settings(c.getSource().getPlayerOrException())
					.withCooldownSeconds(IntegerArgumentType.getInteger(c, "value"))))));
	}

	void tick(MinecraftServer server) {
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			online.add(id);
			cooldownTicks.computeIfPresent(id, (ignored, remaining) -> remaining <= 1 ? null : remaining - 1);
			Settings settings = settings(player);
			boolean observing = settings.enabled && player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS);
			if (!observing) { useTicks.remove(id); triggered.remove(id); continue; }
			int ticks = useTicks.merge(id, 1, Integer::sum);
			if (ticks >= settings.holdTicks && triggered.add(id)) {
				int remaining = cooldownTicks.getOrDefault(id, 0);
				if (remaining > 0) {
					player.displayClientMessage(Component.literal("望远镜发光冷却中：还需 "
						+ (remaining + 19) / 20 + " 秒"), true);
				} else {
					apply(player, settings);
					cooldownTicks.put(id, settings.cooldownTicks);
				}
			}
		}
		useTicks.keySet().retainAll(online);
		cooldownTicks.keySet().retainAll(online);
		triggered.retainAll(online);
	}

	void close() { save(); useTicks.clear(); cooldownTicks.clear(); triggered.clear(); }

	private Settings settings(ServerPlayer player) {
		return saved.getOrDefault(player.getUUID().toString(), Settings.defaults()).normalized();
	}

	private int update(ServerPlayer player, Settings settings) {
		saved.put(player.getUUID().toString(), settings.normalized());
		save();
		return status(player);
	}

	private int status(ServerPlayer player) {
		Settings value = settings(player);
		player.sendSystemMessage(Component.literal("望远镜发光=" + value.enabled + "，半径=" + value.radiusChunks
			+ "区块，观察=" + value.holdTicks / 20 + "秒，持续=" + value.effectTicks / 20
			+ "秒，目标=" + value.targetCondition + "，冷却=" + value.cooldownTicks / 20 + "秒"));
		return 1;
	}

	private static void apply(ServerPlayer player, Settings settings) {
		double radius = settings.radiusChunks * 16.0;
		AABB area = player.getBoundingBox().inflate(radius);
		int affected = 0;
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity != player && entity.isAlive() && matches(settings.targetCondition, entity)
					&& player.distanceToSqr(entity) <= radius * radius)) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, settings.effectTicks, 0, false, false, true));
			affected++;
		}
		player.displayClientMessage(Component.literal("望远镜标记了 " + affected + " 个生物 · 半径 "
			+ settings.radiusChunks + " 区块 · " + settings.targetCondition
			+ " · 持续 " + settings.effectTicks / 20 + " 秒 · 冷却 "
			+ settings.cooldownTicks / 20 + " 秒"), true);
	}

	private static boolean matches(String condition, LivingEntity entity) {
		return switch (condition) {
			case "non_players" -> !(entity instanceof Player);
			case "hostile_only" -> entity instanceof Monster;
			default -> true;
		};
	}

	private void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			Store store = GSON.fromJson(Files.readString(file), Store.class);
			if (store != null && store.players != null) saved.putAll(store.players);
		} catch (Exception error) {
			System.err.println("[AI Companion] 望远镜设置读取失败：" + error.getMessage());
		}
	}

	private synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(new Store(saved)), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception error) {
			System.err.println("[AI Companion] 望远镜设置保存失败：" + error.getMessage());
		}
	}

	private static final class Store {
		Map<String, Settings> players = new HashMap<>();
		Store() {}
		Store(Map<String, Settings> players) { this.players = new HashMap<>(players); }
	}
	private static final class Settings {
		boolean enabled = true;
		int radiusChunks = 10;
		int holdTicks = 20;
		int effectTicks = 2400;
		String targetCondition = "all_living";
		int cooldownTicks = 200;
		static Settings defaults() { return new Settings(); }
		Settings normalized() {
			enabled = enabled; radiusChunks = Math.max(1, Math.min(32, radiusChunks));
			holdTicks = Math.max(20, Math.min(200, holdTicks));
			effectTicks = Math.max(20, Math.min(12000, effectTicks));
			cooldownTicks = cooldownTicks <= 0 ? 200 : Math.max(20, Math.min(12000, cooldownTicks));
			if (!targetCondition.equals("all_living") && !targetCondition.equals("non_players")
					&& !targetCondition.equals("hostile_only")) targetCondition = "all_living";
			return this;
		}
		Settings copy() { Settings value = new Settings(); value.enabled = enabled; value.radiusChunks = radiusChunks; value.holdTicks = holdTicks; value.effectTicks = effectTicks; value.targetCondition = targetCondition; value.cooldownTicks = cooldownTicks; return value; }
		Settings withEnabled(boolean value) { Settings next = copy(); next.enabled = value; return next.normalized(); }
		Settings withRadius(int value) { Settings next = copy(); next.radiusChunks = value; return next.normalized(); }
		Settings withHoldSeconds(int value) { Settings next = copy(); next.holdTicks = value * 20; return next.normalized(); }
		Settings withDurationSeconds(int value) { Settings next = copy(); next.effectTicks = value * 20; return next.normalized(); }
		Settings withTarget(String value) { Settings next = copy(); next.targetCondition = value.toLowerCase(java.util.Locale.ROOT); return next.normalized(); }
		Settings withCooldownSeconds(int value) { Settings next = copy(); next.cooldownTicks = value * 20; return next.normalized(); }
	}
}
