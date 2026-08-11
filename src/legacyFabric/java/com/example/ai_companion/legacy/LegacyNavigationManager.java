package com.example.ai_companion.legacy;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 1.20.1 Fabric navigation service using old ResourceLocation/NBT packet conventions. */
final class LegacyNavigationManager implements AutoCloseable {
	static final ResourceLocation CATALOG_REQUEST = id("navigation_catalog_request");
	static final ResourceLocation CATALOG = id("navigation_catalog");
	static final ResourceLocation LOCATE_REQUEST = id("navigation_locate_request");
	static final ResourceLocation TARGET = id("navigation_target");
	static final ResourceLocation CANCEL_REQUEST = id("navigation_cancel_request");
	static final ResourceLocation STATE = id("navigation_state");
	private static final String ITEM_MARKER = "ai_companion_navigation_owner";
	private static final int MAX_ENTRIES = 4096;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<UUID, Integer> nextSearchTick = new HashMap<>();
	private MinecraftServer server;

	void register() {
		ServerPlayNetworking.registerGlobalReceiver(CATALOG_REQUEST, (minecraftServer, player, handler, buffer, sender) ->
			minecraftServer.execute(() -> sendCatalog(player)));
		ServerPlayNetworking.registerGlobalReceiver(LOCATE_REQUEST, (minecraftServer, player, handler, buffer, sender) -> {
			String type = buffer.readUtf(16);
			String targetId = buffer.readUtf(160);
			minecraftServer.execute(() -> locate(player, type, targetId));
		});
		ServerPlayNetworking.registerGlobalReceiver(CANCEL_REQUEST, (minecraftServer, player, handler, buffer, sender) ->
			minecraftServer.execute(() -> cancel(player, "导航已取消，临时磁石与方向导航指南针已回收", true)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> disconnect(handler.player));
	}

	void tick(MinecraftServer minecraftServer) {
		server = minecraftServer;
		for (Session session : new ArrayList<>(sessions.values())) {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayer(session.playerId);
			if (player == null) continue;
			ServerLevel level = minecraftServer.getLevel(session.dimension);
			if (level == null || !level.getBlockState(session.lodestone).is(Blocks.LODESTONE)) {
				cancel(player, "导航磁石已不存在，路线已取消", true);
				continue;
			}
			if (player.level() == level && horizontal(player.getX(), player.getZ(),
					session.lodestone.getX(), session.lodestone.getZ()) <= 8) {
				cancel(player, "已抵达“" + session.id + "”，临时磁石与方向导航指南针已回收", true);
			}
		}
	}

	private void sendCatalog(ServerPlayer player) {
		List<Entry> entries = new ArrayList<>();
		RegistryAccess access = player.level().registryAccess();
		access.registryOrThrow(Registries.BIOME).keySet().forEach(value -> entries.add(new Entry("biome", value.toString())));
		access.registryOrThrow(Registries.STRUCTURE).keySet().forEach(value -> entries.add(new Entry("structure", value.toString())));
		player.server.getAllLevels().forEach(level -> entries.add(new Entry("dimension", level.dimension().location().toString())));
		entries.sort(Comparator.comparing((Entry value) -> value.type).thenComparing(value -> value.id));
		List<Entry> limited = entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries;
		FriendlyByteBuf response = PacketByteBufs.create();
		response.writeVarInt(limited.size());
		for (Entry entry : limited) { response.writeUtf(entry.type, 16); response.writeUtf(entry.id, 160); }
		response.writeUtf("目录来自服务器动态注册表，包含模组群系和结构", 240);
		ServerPlayNetworking.send(player, CATALOG, response);
	}

	private void locate(ServerPlayer player, String type, String rawId) {
		int now = player.server.getTickCount();
		if (nextSearchTick.getOrDefault(player.getUUID(), 0) > now) {
			sendTarget(player, false, type, rawId, null, BlockPos.ZERO, 0, "导航搜索冷却中，请稍后再试");
			return;
		}
		nextSearchTick.put(player.getUUID(), now + 100);
		try {
			ResourceLocation id = new ResourceLocation(rawId);
			ServerLevel level = player.serverLevel();
			ServerLevel targetLevel = level;
			BlockPos target;
			if ("biome".equals(type)) target = locateBiome(level, player.blockPosition(), id)
				.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该群系"));
			else if ("structure".equals(type)) target = locateStructure(level, player.blockPosition(), id)
				.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该结构"));
			else if ("dimension".equals(type)) {
				targetLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
				if (targetLevel == null) throw new IllegalArgumentException("目标维度当前不可用");
				target = targetLevel.getSharedSpawnPos();
			} else throw new IllegalArgumentException("不支持的导航类型");
			Started started = begin(player, type, rawId, targetLevel, target);
			sendTarget(player, true, type, rawId, targetLevel, started.target, started.startingDistance,
				"导航已开始；方向导航指南针已绑定临时磁石");
		} catch (RuntimeException error) {
			sendTarget(player, false, type, rawId, null, BlockPos.ZERO, 0,
				error.getMessage() == null ? "导航搜索失败" : error.getMessage());
		}
	}

	private Started begin(ServerPlayer player, String type, String id, ServerLevel level, BlockPos target) {
		server = player.server;
		cancel(player, "", false);
		BlockPos lodestone = placement(player, level, target);
		BlockState previous = level.getBlockState(lodestone);
		if (!level.setBlockAndUpdate(lodestone, Blocks.LODESTONE.defaultBlockState())) {
			throw new IllegalStateException("无法在目标附近生成临时磁石");
		}
		double starting = player.level() == level ? horizontal(player.getX(), player.getZ(), lodestone.getX(), lodestone.getZ()) : -1;
		ItemStack compass = new ItemStack(Items.COMPASS);
		compass.setHoverName(Component.literal("方向导航"));
		CompoundTag tag = compass.getOrCreateTag();
		tag.put("LodestonePos", NbtUtils.writeBlockPos(lodestone));
		tag.putString("LodestoneDimension", level.dimension().location().toString());
		tag.putBoolean("LodestoneTracked", true);
		tag.putString(ITEM_MARKER, player.getUUID().toString());
		if (!player.getInventory().add(compass)) player.drop(compass, false);
		else { int slot = player.getInventory().findSlotMatchingItem(compass); if (slot >= 0) player.getInventory().pickSlot(slot); }
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges();
		sessions.put(player.getUUID(), new Session(player.getUUID(), type, id, level.dimension(), lodestone, previous));
		return new Started(lodestone, starting);
	}

	private BlockPos placement(ServerPlayer player, ServerLevel level, BlockPos target) {
		for (int attempt = 0; attempt < 32; attempt++) {
			int radius = attempt == 0 ? 0 : 3 + player.getRandom().nextInt(10);
			int x = target.getX() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius);
			int z = target.getZ() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius);
			BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (level.getBlockState(candidate).canBeReplaced()) return candidate;
		}
		throw new IllegalStateException("目标附近没有可安全放置临时磁石的位置");
	}

	private Optional<BlockPos> locateBiome(ServerLevel level, BlockPos origin, ResourceLocation id) {
		Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
		if (!registry.containsKey(id)) return Optional.empty();
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(key), origin, 6400, 32, 64);
		return Optional.ofNullable(found).map(Pair::getFirst);
	}

	private Optional<BlockPos> locateStructure(ServerLevel level, BlockPos origin, ResourceLocation id) {
		Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id));
		if (holder.isEmpty()) return Optional.empty();
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder.get()), origin, 100, false);
		return Optional.ofNullable(found).map(Pair::getFirst);
	}

	private void sendTarget(ServerPlayer player, boolean success, String type, String id, ServerLevel level,
			BlockPos target, double starting, String message) {
		FriendlyByteBuf response = PacketByteBufs.create();
		response.writeBoolean(success); response.writeUtf(type, 16); response.writeUtf(id, 160);
		response.writeUtf(level == null ? "" : level.dimension().location().toString(), 160);
		response.writeDouble(target.getX()); response.writeDouble(target.getY()); response.writeDouble(target.getZ());
		response.writeDouble(starting); response.writeUtf(message, 300);
		ServerPlayNetworking.send(player, TARGET, response);
	}

	private synchronized void disconnect(ServerPlayer player) { cancel(player, "", false); }
	private synchronized void cancel(ServerPlayer player, String message, boolean notify) {
		Session session = sessions.remove(player.getUUID());
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.COMPASS) && stack.hasTag()
					&& player.getUUID().toString().equals(stack.getTag().getString(ITEM_MARKER))) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
		player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
		if (session != null) restore(session);
		if (notify) {
			FriendlyByteBuf response = PacketByteBufs.create(); response.writeBoolean(false); response.writeUtf(message, 300);
			ServerPlayNetworking.send(player, STATE, response); player.sendSystemMessage(Component.literal(message));
		}
	}

	private void restore(Session session) {
		if (server == null) return;
		ServerLevel level = server.getLevel(session.dimension);
		if (level != null && level.getBlockState(session.lodestone).is(Blocks.LODESTONE)) {
			level.setBlockAndUpdate(session.lodestone, session.previous);
		}
	}

	@Override public synchronized void close() {
		if (server != null) for (Session session : new ArrayList<>(sessions.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
			if (player != null) cancel(player, "", false); else restore(session);
		}
		sessions.clear(); nextSearchTick.clear(); server = null;
	}

	private static double horizontal(double x, double z, double tx, double tz) { return Math.hypot(tx - x, tz - z); }
	private static ResourceLocation id(String path) { return new ResourceLocation(LegacyFabricMod.MOD_ID, path); }
	private record Entry(String type, String id) { }
	private record Started(BlockPos target, double startingDistance) { }
	private record Session(UUID playerId, String type, String id, ResourceKey<Level> dimension,
		BlockPos lodestone, BlockState previous) { }
}
