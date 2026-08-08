package com.example.ai_companion.spyglass;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies the vanilla spectral-arrow glowing effect after a continuous spyglass observation. */
public final class SpyglassHighlightManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-spyglass.json");
	private final Map<String, SpyglassHighlightSettings> saved = new HashMap<>();
	private final Map<UUID, Integer> useTicks = new HashMap<>();
	private final Map<UUID, Integer> cooldownTicks = new HashMap<>();
	private final Set<UUID> triggered = new HashSet<>();

	public SpyglassHighlightManager() { load(); }

	public SpyglassHighlightSettings settings(UUID playerId) {
		return saved.getOrDefault(playerId.toString(), SpyglassHighlightSettings.defaults()).normalized();
	}

	public synchronized void update(UUID playerId, SpyglassHighlightSettings settings) {
		saved.put(playerId.toString(), settings.normalized());
		save();
	}

	public void tick(MinecraftServer server) {
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player instanceof FakePlayer) continue;
			UUID id = player.getUUID();
			online.add(id);
			cooldownTicks.computeIfPresent(id, (ignored, remaining) -> remaining <= 1 ? null : remaining - 1);
			SpyglassHighlightSettings settings = settings(id);
			boolean observing = settings.enabled() && player.isUsingItem()
				&& player.getUseItem().is(Items.SPYGLASS);
			if (!observing) {
				useTicks.remove(id);
				triggered.remove(id);
				continue;
			}
			int ticks = useTicks.merge(id, 1, Integer::sum);
			if (ticks >= settings.holdTicks() && triggered.add(id)) {
				int remaining = cooldownTicks.getOrDefault(id, 0);
				if (remaining > 0) {
					player.sendOverlayMessage(Component.literal("望远镜发光冷却中：还需 "
						+ (remaining + 19) / 20 + " 秒"));
				} else {
					apply(player, settings);
					cooldownTicks.put(id, settings.cooldownTicks());
				}
			}
		}
		useTicks.keySet().retainAll(online);
		cooldownTicks.keySet().retainAll(online);
		triggered.retainAll(online);
	}

	private static void apply(ServerPlayer player, SpyglassHighlightSettings settings) {
		double radius = settings.radiusChunks() * 16.0;
		AABB area = player.getBoundingBox().inflate(radius);
		int affected = 0;
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity != player && entity.isAlive() && settings.targetCondition().matches(entity)
					&& player.distanceToSqr(entity) <= radius * radius)) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, settings.effectTicks(), 0,
				false, false, true));
			affected++;
		}
		player.sendOverlayMessage(Component.literal("望远镜标记了 " + affected + " 个生物 · 半径 "
			+ settings.radiusChunks() + " 区块 · " + settings.targetCondition().displayName()
			+ " · 持续 " + settings.effectTicks() / 20 + " 秒 · 冷却 "
			+ settings.cooldownTicks() / 20 + " 秒"));
	}

	public void close() { save(); useTicks.clear(); cooldownTicks.clear(); triggered.clear(); }

	@SuppressWarnings("unchecked")
	private void load() {
		if (!Files.isRegularFile(PATH)) return;
		try {
			Store store = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Store.class);
			if (store != null && store.players != null) saved.putAll(store.players);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read spyglass settings {}; using defaults", PATH, error);
		}
	}

	private synchronized void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(new Store(saved)), StandardCharsets.UTF_8);
			Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save spyglass settings {}", PATH, error);
		}
	}

	private record Store(Map<String, SpyglassHighlightSettings> players) {}
}
