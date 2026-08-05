package com.example.ai_companion.exploration;

import com.mojang.datafixers.util.Pair;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.network.NavigationHudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Locates biome/structure targets and streams compact AR-navigation state to clients. */
public final class ExplorerNavigationManager implements AutoCloseable {
	public static final double BORDERLANDS_DISTANCE = 12_550_821.0;
	private static final int HUD_UPDATE_INTERVAL = 5;
	private static final double ARRIVAL_DISTANCE = 8.0;

	private static final class Preference {
		NavigationMode mode = NavigationMode.NAVIGATE;
		NavigationTargetType type = NavigationTargetType.BIOME;
		String targetId = "minecraft:plains";
		Session session;
	}

	private record Session(NavigationMode mode, NavigationTargetType type, String targetId,
		String dimension, double x, double y, double z, double initialDistance) { }

	private final Supplier<GameplayConfig> config;
	private final Map<UUID, Preference> preferences = new HashMap<>();

	public ExplorerNavigationManager(Supplier<GameplayConfig> config) {
		this.config = config;
	}

	public synchronized void setMode(ServerPlayer player, NavigationMode mode) {
		preference(player).mode = mode;
	}

	public synchronized void setTarget(ServerPlayer player, NavigationTargetType type, String targetId) {
		Preference preference = preference(player);
		preference.type = type;
		preference.targetId = type == NavigationTargetType.BORDERLANDS
			? "ai_companion:borderlands" : validateIdentifier(targetId);
		preference.session = null;
	}

	public synchronized String status(ServerPlayer player) {
		Preference preference = preference(player);
		String active = preference.session == null ? "未开始" : "导航中";
		return "结构群系指南针=" + (config.get().explorerNavigatorEnabled() ? "开启" : "关闭")
			+ "，模式=" + preference.mode.label() + "，目标=" + preference.type.label()
			+ ":" + preference.targetId + "，状态=" + active;
	}

	public synchronized NavigationSnapshot start(ServerPlayer player) {
		if (!config.get().explorerNavigatorEnabled()) {
			throw new IllegalStateException("结构群系指南针默认关闭，请先由管理员启用");
		}
		Preference preference = preference(player);
		if (preference.type == NavigationTargetType.BORDERLANDS
				&& !config.get().worldLimitsRemoved()) {
			throw new IllegalStateException("导航到边境之地前必须启用实验性世界限制解除");
		}
		ServerLevel level = player.level();
		BlockPos found = switch (preference.type) {
			case BIOME -> locateBiome(level, player.blockPosition(), preference.targetId);
			case STRUCTURE -> locateStructure(level, player.blockPosition(), preference.targetId);
			case BORDERLANDS -> nearestBorderlands(player);
		};
		double targetY = safeSurfaceY(level, found.getX(), found.getZ(), found.getY());
		double distance = horizontalDistance(player.getX(), player.getZ(), found.getX() + 0.5, found.getZ() + 0.5);
		if (preference.mode == NavigationMode.TELEPORT) {
			player.teleportTo(found.getX() + 0.5, targetY, found.getZ() + 0.5);
			preference.session = null;
			NavigationSnapshot arrived = snapshot(player, new Session(preference.mode, preference.type,
				preference.targetId, level.dimension().identifier().toString(), found.getX() + 0.5,
				targetY, found.getZ() + 0.5, Math.max(1.0, distance)), false);
			send(player, arrived);
			return arrived;
		}
		preference.session = new Session(preference.mode, preference.type, preference.targetId,
			level.dimension().identifier().toString(), found.getX() + 0.5, targetY,
			found.getZ() + 0.5, Math.max(1.0, distance));
		NavigationSnapshot started = snapshot(player, preference.session, true);
		send(player, started);
		return started;
	}

	public synchronized void stop(ServerPlayer player) {
		preference(player).session = null;
		send(player, NavigationSnapshot.inactive());
	}

	public synchronized void tick(MinecraftServer server) {
		if (server.getTickCount() % HUD_UPDATE_INTERVAL != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Preference preference = preferences.get(player.getUUID());
			if (preference == null || preference.session == null) continue;
			Session session = preference.session;
			if (!player.level().dimension().identifier().toString().equals(session.dimension)) {
				send(player, snapshot(player, session, true));
				continue;
			}
			NavigationSnapshot update = snapshot(player, session, true);
			if (update.distance() <= ARRIVAL_DISTANCE) {
				preference.session = null;
				send(player, new NavigationSnapshot(false, session.mode, session.type, session.targetId,
					session.dimension, session.x, session.y, session.z, update.distance(),
					session.initialDistance, update.relativeBearing(), update.verticalDifference()));
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[导航] 已到达 "
					+ session.type.label() + " " + session.targetId));
			} else {
				send(player, update);
			}
		}
	}

	private static BlockPos locateBiome(ServerLevel level, BlockPos origin, String targetId) {
		Identifier id = Identifier.parse(targetId);
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		Holder.Reference<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME)
			.get(key).orElseThrow(() -> new IllegalArgumentException("找不到群系: " + targetId));
		Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(candidate -> candidate.is(key),
			origin, 6_400, 128, 32);
		if (result == null) throw new IllegalStateException("在 6400 格内找不到群系: " + holder.getRegisteredName());
		return result.getFirst();
	}

	private static BlockPos locateStructure(ServerLevel level, BlockPos origin, String targetId) {
		Identifier id = Identifier.parse(targetId);
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(key).orElseThrow(() -> new IllegalArgumentException("找不到结构: " + targetId));
		Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
		if (result == null) throw new IllegalStateException("在 100 区块内找不到结构: " + targetId);
		return result.getFirst();
	}

	private static BlockPos nearestBorderlands(ServerPlayer player) {
		double x = player.getX();
		double z = player.getZ();
		double xSign = x < 0 ? -1.0 : 1.0;
		double zSign = z < 0 ? -1.0 : 1.0;
		double xDistance = Math.abs(BORDERLANDS_DISTANCE - Math.abs(x));
		double zDistance = Math.abs(BORDERLANDS_DISTANCE - Math.abs(z));
		return xDistance <= zDistance
			? BlockPos.containing(xSign * BORDERLANDS_DISTANCE, player.getY(), z)
			: BlockPos.containing(x, player.getY(), zSign * BORDERLANDS_DISTANCE);
	}

	private static double safeSurfaceY(ServerLevel level, int x, int z, int fallback) {
		int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
		int minimum = level.getMinY() + 2;
		int maximum = level.getMinY() + level.getHeight() - 2;
		return Math.clamp(height <= minimum ? fallback : height, minimum, maximum);
	}

	private static NavigationSnapshot snapshot(ServerPlayer player, Session session, boolean active) {
		double distance = player.level().dimension().identifier().toString().equals(session.dimension)
			? horizontalDistance(player.getX(), player.getZ(), session.x, session.z)
			: session.initialDistance;
		double desiredYaw = Math.toDegrees(Math.atan2(-(session.x - player.getX()), session.z - player.getZ()));
		float bearing = Mth.wrapDegrees((float) desiredYaw - player.getYRot());
		return new NavigationSnapshot(active, session.mode, session.type, session.targetId,
			session.dimension, session.x, session.y, session.z, distance, session.initialDistance,
			bearing, session.y - player.getY());
	}

	private static double horizontalDistance(double x1, double z1, double x2, double z2) {
		return Math.hypot(x2 - x1, z2 - z1);
	}

	private Preference preference(ServerPlayer player) {
		return preferences.computeIfAbsent(player.getUUID(), ignored -> new Preference());
	}

	private static String validateIdentifier(String value) {
		String normalized = value == null ? "" : value.strip().toLowerCase();
		Identifier.parse(normalized);
		return normalized;
	}

	private static void send(ServerPlayer player, NavigationSnapshot snapshot) {
		if (ServerPlayNetworking.canSend(player, NavigationHudPayload.TYPE)) {
			ServerPlayNetworking.send(player, new NavigationHudPayload(snapshot));
		}
	}

	@Override
	public synchronized void close() {
		preferences.clear();
	}
}
