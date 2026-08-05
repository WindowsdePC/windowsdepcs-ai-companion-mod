package com.example.ai_companion.navigation;

import com.example.ai_companion.world.WorldFeatureConfig;
import com.example.ai_companion.world.WorldFeatureManager;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Server-authoritative catalogue, locate and permission-checked teleport service. */
public final class NavigationNetworking {
	private NavigationNetworking() {
	}

	public static void registerServer(Supplier<WorldFeatureConfig> config) {
		PayloadTypeRegistry.serverboundPlay().register(NavigationCatalogRequestPayload.TYPE,
			NavigationCatalogRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NavigationCatalogPayload.TYPE,
			NavigationCatalogPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NavigationLocateRequestPayload.TYPE,
			NavigationLocateRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NavigationTargetPayload.TYPE,
			NavigationTargetPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NavigationCatalogRequestPayload.TYPE,
			(payload, context) -> context.responseSender().sendPacket(catalog(context.player(), config.get())));
		ServerPlayNetworking.registerGlobalReceiver(NavigationLocateRequestPayload.TYPE,
			(payload, context) -> context.responseSender().sendPacket(locate(context.player(), payload, config.get())));
	}

	private static NavigationCatalogPayload catalog(ServerPlayer player, WorldFeatureConfig config) {
		if (!config.navigatorEnabled()) {
			return new NavigationCatalogPayload(false, List.of(), "服务器尚未开启结构与群系导航");
		}
		List<NavigationEntry> entries = new ArrayList<>();
		Registry<Biome> biomes = player.level().registryAccess().lookupOrThrow(Registries.BIOME);
		biomes.keySet().forEach(id -> entries.add(new NavigationEntry("biome", id.toString())));
		Registry<Structure> structures = player.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		structures.keySet().forEach(id -> entries.add(new NavigationEntry("structure", id.toString())));
		player.level().getServer().levelKeys().forEach(key -> entries.add(new NavigationEntry("dimension",
			key.identifier().toString())));
		if (config.maximumWorldBorderEnabled()) {
			entries.add(new NavigationEntry("special", "ai_companion:border_lands"));
		}
		entries.sort(Comparator.comparing(NavigationEntry::type).thenComparing(NavigationEntry::id));
		return new NavigationCatalogPayload(true,
			entries.stream().limit(NavigationCatalogPayload.MAX_ENTRIES).toList(),
			"搜索结果来自当前服务器的动态注册表");
	}

	private static NavigationTargetPayload locate(ServerPlayer player, NavigationLocateRequestPayload request,
			WorldFeatureConfig config) {
		if (!config.navigatorEnabled()) return failure(request, "服务器尚未开启结构与群系导航");
		try {
			ServerLevel level = player.level();
			Identifier id = Identifier.parse(request.id());
			BlockPos target;
			ServerLevel targetLevel = level;
			switch (request.targetType()) {
				case "biome" -> target = locateBiome(level, player.blockPosition(), id)
					.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该群系"));
				case "structure" -> target = locateStructure(level, player.blockPosition(), id)
					.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该结构"));
				case "dimension" -> {
					ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
					targetLevel = player.level().getServer().getLevel(dimension);
					if (targetLevel == null) throw new IllegalArgumentException("目标维度当前不可用");
					target = targetLevel.getRespawnData().pos();
				}
				case "special" -> {
					if (!request.id().equals("ai_companion:border_lands")
							|| !config.maximumWorldBorderEnabled()) {
						throw new IllegalArgumentException("边境之地导航需要先开启原版最大世界边界");
					}
					target = BlockPos.containing(WorldFeatureManager.BORDER_LANDS_COORDINATE,
						player.getY(), WorldFeatureManager.BORDER_LANDS_COORDINATE);
				}
				default -> throw new IllegalArgumentException("不支持的导航类型");
			}
			int surface = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				target.getX(), target.getZ());
			target = new BlockPos(target.getX(), Math.max(target.getY(), surface + 1), target.getZ());
			double distance = player.level() == targetLevel
				? NavigationMath.horizontalDistance(player.getX(), player.getZ(), target.getX(), target.getZ()) : -1;
			if (request.teleport()) {
				if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
					throw new IllegalArgumentException("传送模式仅允许管理员使用");
				}
				player.teleportTo(targetLevel, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
					java.util.Set.of(), player.getYRot(), player.getXRot(), false);
				return success(request, targetLevel, target, 0, "已传送到目标附近");
			}
			return success(request, targetLevel, target, distance, "AR 导航已开始");
		} catch (RuntimeException error) {
			return failure(request, error.getMessage() == null ? "导航搜索失败" : error.getMessage());
		}
	}

	private static Optional<BlockPos> locateBiome(ServerLevel level, BlockPos origin, Identifier id) {
		Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		if (!registry.containsKey(key)) return Optional.empty();
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(key), origin,
			6_400, 32, 64);
		return Optional.ofNullable(found).map(Pair::getFirst);
	}

	private static Optional<BlockPos> locateStructure(ServerLevel level, BlockPos origin, Identifier id) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<Holder.Reference<Structure>> holder = registry.get(id);
		if (holder.isEmpty()) return Optional.empty();
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder.get()), origin, 100, false);
		return Optional.ofNullable(found).map(Pair::getFirst);
	}

	private static NavigationTargetPayload success(NavigationLocateRequestPayload request,
			ServerLevel level, BlockPos target, double distance, String message) {
		return new NavigationTargetPayload(true, request.targetType(), request.id(),
			level.dimension().identifier().toString(), target.getX(), target.getY(), target.getZ(),
			distance, message);
	}

	private static NavigationTargetPayload failure(NavigationLocateRequestPayload request, String message) {
		return new NavigationTargetPayload(false, request.targetType(), request.id(), "", 0, 0, 0, 0, message);
	}
}
