package com.example.ai_companion.travel;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.photo.PhotoEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Automatically observes biomes and structures and maintains player adventure compendiums. */
public final class TravelLogManager implements AutoCloseable {
	private static final int SAMPLE_INTERVAL_TICKS = 40;
	private static final double MAX_PHOTO_LINK_DISTANCE = 256.0;
	private static final Set<String> RUIN_MARKERS = Set.of(
		"ruin", "temple", "mineshaft", "stronghold", "ancient_city", "monument",
		"mansion", "fortress", "bastion", "end_city", "shipwreck", "igloo",
		"outpost", "trial_chambers");

	private record Sample(String dimension, int chunkX, int chunkZ) { }

	private final TravelLogStore store;
	private final Map<UUID, Sample> lastSamples = new HashMap<>();
	private final Set<UUID> fullWarnings = new HashSet<>();

	public TravelLogManager() {
		this(TravelLogStore.load());
	}

	TravelLogManager(TravelLogStore store) {
		this.store = store;
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % SAMPLE_INTERVAL_TICKS != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				observe(player);
			} catch (IllegalStateException full) {
				if (fullWarnings.add(player.getUUID())) {
					player.sendSystemMessage(Component.literal("[旅行图鉴] " + full.getMessage()));
				}
			} catch (RuntimeException | IOException error) {
				AiCompanionMod.LOGGER.error("Cannot update travel journal for {}",
					player.getScoreboardName(), error);
			}
		}
	}

	public List<TravelLogEntry> entries(ServerPlayer player) {
		return store.entries(player.getUUID());
	}

	public TravelLogEntry require(ServerPlayer player, long id) {
		return store.require(player.getUUID(), id);
	}

	public Map<TravelLogCategory, Long> categoryCounts(ServerPlayer player) {
		return store.categoryCounts(player.getUUID());
	}

	public TravelLogEntry linkPhoto(ServerPlayer player, long entryId, PhotoEntry photo)
			throws IOException {
		TravelLogEntry entry = require(player, entryId);
		if (!entry.dimension().equals(photo.dimension())) {
			throw new IllegalArgumentException("照片与旅行地点不在同一维度");
		}
		double dx = entry.x() - photo.x();
		double dy = entry.y() - photo.y();
		double dz = entry.z() - photo.z();
		if (dx * dx + dy * dy + dz * dz > MAX_PHOTO_LINK_DISTANCE * MAX_PHOTO_LINK_DISTANCE) {
			throw new IllegalArgumentException("照片必须在旅行地点 256 格范围内拍摄");
		}
		return store.linkPhoto(player.getUUID(), entryId, photo.id());
	}

	public TravelLogEntry unlinkPhoto(ServerPlayer player, long entryId) throws IOException {
		return store.linkPhoto(player.getUUID(), entryId, 0);
	}

	private void observe(ServerPlayer player) throws IOException {
		ServerLevel level = player.level();
		BlockPos position = player.blockPosition();
		String dimension = level.dimension().identifier().toString();
		Sample sample = new Sample(dimension, position.getX() >> 4, position.getZ() >> 4);
		if (sample.equals(lastSamples.put(player.getUUID(), sample))) return;

		record(player, TravelLogCategory.SPECIAL, "dimension:" + dimension,
			"进入维度 " + dimension, dimension, position);

		String biome = level.getBiome(position).unwrapKey()
			.map(key -> key.identifier().toString()).orElse("unknown");
		record(player, TravelLogCategory.BIOME, "biome:" + dimension + ":" + biome,
			biome, dimension, position);

		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Structure structure : level.structureManager().getAllStructuresAt(position).keySet()) {
			Identifier id = structures.getKey(structure);
			if (id == null) continue;
			TravelLogCategory category = categoryFor(id);
			String region = Math.floorDiv(position.getX(), 128) + ":" + Math.floorDiv(position.getZ(), 128);
			record(player, category, "structure:" + dimension + ":" + id + ":" + region,
				id.toString(), dimension, position);
		}

		if (level.isVillage(position)) {
			String region = Math.floorDiv(position.getX(), 128) + ":" + Math.floorDiv(position.getZ(), 128);
			record(player, TravelLogCategory.VILLAGE, "village:" + dimension + ":" + region,
				"村庄", dimension, position);
		}
	}

	private void record(ServerPlayer player, TravelLogCategory category, String key, String name,
			String dimension, BlockPos position) throws IOException {
		TravelLogEntry entry = store.addIfAbsent(player.getUUID(), category, key, name, dimension,
			player.getX(), player.getY(), player.getZ(), System.currentTimeMillis());
		if (entry == null) return;
		player.sendOverlayMessage(Component.literal(String.format(Locale.ROOT,
			"旅行图鉴新增：[%s] %s", category.displayName(), name)));
	}

	private static TravelLogCategory categoryFor(Identifier id) {
		String path = id.getPath().toLowerCase(Locale.ROOT);
		if (path.contains("village")) return TravelLogCategory.VILLAGE;
		for (String marker : RUIN_MARKERS) {
			if (path.contains(marker)) return TravelLogCategory.RUIN;
		}
		return TravelLogCategory.SPECIAL;
	}

	@Override
	public void close() {
		lastSamples.clear();
		fullWarnings.clear();
		store.close();
	}
}
