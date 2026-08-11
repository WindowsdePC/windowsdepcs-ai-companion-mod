package com.example.ai_companion.legacy;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Forge 47 / Minecraft 1.20.1 navigation packets and server-authoritative route lifecycle. */
final class LegacyForgeNavigation implements AutoCloseable {
	private static final String PROTOCOL = "1";
	private static final String ITEM_MARKER = "ai_companion_navigation_owner";
	static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(LegacyForgeMod.MOD_ID, "navigation"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<UUID, Integer> nextSearchTick = new HashMap<>();
	private MinecraftServer server;

	void register() {
		int id = 0;
		CHANNEL.registerMessage(id++, CatalogRequest.class, CatalogRequest::encode, CatalogRequest::decode, CatalogRequest::handle);
		CHANNEL.registerMessage(id++, CatalogResponse.class, CatalogResponse::encode, CatalogResponse::decode, CatalogResponse::handle);
		CHANNEL.registerMessage(id++, LocateRequest.class, LocateRequest::encode, LocateRequest::decode, LocateRequest::handle);
		CHANNEL.registerMessage(id++, TargetResponse.class, TargetResponse::encode, TargetResponse::decode, TargetResponse::handle);
		CHANNEL.registerMessage(id++, CancelRequest.class, CancelRequest::encode, CancelRequest::decode, CancelRequest::handle);
		CHANNEL.registerMessage(id, StateResponse.class, StateResponse::encode, StateResponse::decode, StateResponse::handle);
	}

	void tick(MinecraftServer minecraftServer) {
		server = minecraftServer;
		for (Session session : new ArrayList<>(sessions.values())) {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayer(session.playerId);
			if (player == null) continue;
			ServerLevel level = minecraftServer.getLevel(session.dimension);
			if (level == null || !level.getBlockState(session.lodestone).is(Blocks.LODESTONE)) {
				cancel(player, "导航磁石已不存在，路线已取消", true); continue;
			}
			if (player.level() == level && horizontal(player.getX(), player.getZ(), session.lodestone.getX(), session.lodestone.getZ()) <= 8) {
				cancel(player, "已抵达“" + session.id + "”，临时磁石与方向导航指南针已回收", true);
			}
		}
	}

	void disconnect(ServerPlayer player) { cancel(player, "", false); }

	private void sendCatalog(ServerPlayer player) {
		List<Entry> values = new ArrayList<>();
		player.level().registryAccess().registryOrThrow(Registries.BIOME).keySet().forEach(id -> values.add(new Entry("biome", id.toString())));
		player.level().registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().forEach(id -> values.add(new Entry("structure", id.toString())));
		player.server.getAllLevels().forEach(level -> values.add(new Entry("dimension", level.dimension().location().toString())));
		values.sort(Comparator.comparing((Entry value) -> value.type).thenComparing(value -> value.id));
		if (values.size() > 4096) values = List.copyOf(values.subList(0, 4096));
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
			new CatalogResponse(values, "目录来自服务器动态注册表，包含模组群系和结构"));
	}

	private void locate(ServerPlayer player, String type, String rawId) {
		int now = player.server.getTickCount();
		if (nextSearchTick.getOrDefault(player.getUUID(), 0) > now) {
			sendTarget(player, false, type, rawId, null, BlockPos.ZERO, 0, "导航搜索冷却中，请稍后再试"); return;
		}
		nextSearchTick.put(player.getUUID(), now + 100);
		try {
			ResourceLocation id = new ResourceLocation(rawId);
			ServerLevel level = player.serverLevel(), targetLevel = level; BlockPos target;
			if ("biome".equals(type)) target = locateBiome(level, player.blockPosition(), id)
				.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该群系"));
			else if ("structure".equals(type)) target = locateStructure(level, player.blockPosition(), id)
				.orElseThrow(() -> new IllegalArgumentException("搜索半径内未找到该结构"));
			else if ("dimension".equals(type)) { targetLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id)); if (targetLevel == null) throw new IllegalArgumentException("目标维度当前不可用"); target = targetLevel.getSharedSpawnPos(); }
			else throw new IllegalArgumentException("不支持的导航类型");
			Started started = begin(player, type, rawId, targetLevel, target);
			sendTarget(player, true, type, rawId, targetLevel, started.target, started.startingDistance,
				"导航已开始；方向导航指南针已绑定临时磁石");
		} catch (RuntimeException error) {
			sendTarget(player, false, type, rawId, null, BlockPos.ZERO, 0,
				error.getMessage() == null ? "导航搜索失败" : error.getMessage());
		}
	}

	private Started begin(ServerPlayer player, String type, String id, ServerLevel level, BlockPos target) {
		server = player.server; cancel(player, "", false);
		BlockPos lodestone = placement(player, level, target); BlockState previous = level.getBlockState(lodestone);
		if (!level.setBlockAndUpdate(lodestone, Blocks.LODESTONE.defaultBlockState())) throw new IllegalStateException("无法在目标附近生成临时磁石");
		double starting = player.level() == level ? horizontal(player.getX(), player.getZ(), lodestone.getX(), lodestone.getZ()) : -1;
		ItemStack compass = new ItemStack(Items.COMPASS); compass.setHoverName(Component.literal("方向导航")); CompoundTag tag = compass.getOrCreateTag();
		tag.put("LodestonePos", NbtUtils.writeBlockPos(lodestone)); tag.putString("LodestoneDimension", level.dimension().location().toString());
		tag.putBoolean("LodestoneTracked", true); tag.putString(ITEM_MARKER, player.getUUID().toString());
		if (!player.getInventory().add(compass)) player.drop(compass, false);
		else { int slot = player.getInventory().findSlotMatchingItem(compass); if (slot >= 0) player.getInventory().pickSlot(slot); }
		player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
		sessions.put(player.getUUID(), new Session(player.getUUID(), type, id, level.dimension(), lodestone, previous));
		return new Started(lodestone, starting);
	}

	private BlockPos placement(ServerPlayer player, ServerLevel level, BlockPos target) {
		for (int attempt = 0; attempt < 32; attempt++) { int radius = attempt == 0 ? 0 : 3 + player.getRandom().nextInt(10); int x = target.getX() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius); int z = target.getZ() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius); BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z); if (level.getBlockState(candidate).canBeReplaced()) return candidate; }
		throw new IllegalStateException("目标附近没有可安全放置临时磁石的位置");
	}

	private Optional<BlockPos> locateBiome(ServerLevel level, BlockPos origin, ResourceLocation id) {
		Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME); ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id); if (!registry.containsKey(id)) return Optional.empty(); Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(key), origin, 6400, 32, 64); return Optional.ofNullable(found).map(Pair::getFirst);
	}
	private Optional<BlockPos> locateStructure(ServerLevel level, BlockPos origin, ResourceLocation id) {
		Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE); Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id)); if (holder.isEmpty()) return Optional.empty(); Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), origin, 100, false); return Optional.ofNullable(found).map(Pair::getFirst);
	}

	private void sendTarget(ServerPlayer player, boolean success, String type, String id, ServerLevel level, BlockPos target, double starting, String message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TargetResponse(success, type, id,
			level == null ? "" : level.dimension().location().toString(), target.getX(), target.getY(), target.getZ(), starting, message));
	}

	private synchronized void cancel(ServerPlayer player, String message, boolean notify) {
		Session session = sessions.remove(player.getUUID());
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) { ItemStack stack = player.getInventory().getItem(slot); if (stack.is(Items.COMPASS) && stack.hasTag() && player.getUUID().toString().equals(stack.getTag().getString(ITEM_MARKER))) player.getInventory().setItem(slot, ItemStack.EMPTY); }
		player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); if (session != null) restore(session);
		if (notify) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StateResponse(false, message)); player.sendSystemMessage(Component.literal(message)); }
	}
	private void restore(Session session) { if (server == null) return; ServerLevel level = server.getLevel(session.dimension); if (level != null && level.getBlockState(session.lodestone).is(Blocks.LODESTONE)) level.setBlockAndUpdate(session.lodestone, session.previous); }
	@Override public synchronized void close() { if (server != null) for (Session session : new ArrayList<>(sessions.values())) { ServerPlayer player = server.getPlayerList().getPlayer(session.playerId); if (player != null) cancel(player, "", false); else restore(session); } sessions.clear(); nextSearchTick.clear(); server = null; }

	static void requestCatalog() { CHANNEL.sendToServer(new CatalogRequest()); }
	static void requestLocate(String type, String id) { CHANNEL.sendToServer(new LocateRequest(type, id)); }
	static void requestCancel() { CHANNEL.sendToServer(new CancelRequest()); }
	private static double horizontal(double x, double z, double tx, double tz) { return Math.hypot(tx - x, tz - z); }
	private record Started(BlockPos target, double startingDistance) { }
	private record Session(UUID playerId, String type, String id, ResourceKey<Level> dimension, BlockPos lodestone, BlockState previous) { }
	static record Entry(String type, String id) { }

	private record CatalogRequest() {
		static void encode(CatalogRequest packet, FriendlyByteBuf buffer) { }
		static CatalogRequest decode(FriendlyByteBuf buffer) { return new CatalogRequest(); }
		static void handle(CatalogRequest packet, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender(); context.enqueueWork(() -> { if (player != null) LegacyForgeMod.navigation().sendCatalog(player); }); context.setPacketHandled(true); }
	}
	private record CatalogResponse(List<Entry> entries, String message) {
		static void encode(CatalogResponse packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.entries.size()); for (Entry entry : packet.entries) { buffer.writeUtf(entry.type, 16); buffer.writeUtf(entry.id, 160); } buffer.writeUtf(packet.message, 240); }
		static CatalogResponse decode(FriendlyByteBuf buffer) { int count = Math.min(4096, Math.max(0, buffer.readVarInt())); List<Entry> entries = new ArrayList<>(count); for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readUtf(16), buffer.readUtf(160))); return new CatalogResponse(entries, buffer.readUtf(240)); }
		static void handle(CatalogResponse packet, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LegacyForgeNavigationClient.acceptCatalog(packet.entries, packet.message))); context.setPacketHandled(true); }
	}
	private record LocateRequest(String type, String id) {
		static void encode(LocateRequest packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.type, 16); buffer.writeUtf(packet.id, 160); }
		static LocateRequest decode(FriendlyByteBuf buffer) { return new LocateRequest(buffer.readUtf(16), buffer.readUtf(160)); }
		static void handle(LocateRequest packet, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender(); context.enqueueWork(() -> { if (player != null) LegacyForgeMod.navigation().locate(player, packet.type, packet.id); }); context.setPacketHandled(true); }
	}
	private record TargetResponse(boolean success, String type, String id, String dimension, double x, double y, double z, double starting, String message) {
		static void encode(TargetResponse p, FriendlyByteBuf b) { b.writeBoolean(p.success); b.writeUtf(p.type, 16); b.writeUtf(p.id, 160); b.writeUtf(p.dimension, 160); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeDouble(p.starting); b.writeUtf(p.message, 300); }
		static TargetResponse decode(FriendlyByteBuf b) { return new TargetResponse(b.readBoolean(), b.readUtf(16), b.readUtf(160), b.readUtf(160), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readUtf(300)); }
		static void handle(TargetResponse p, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LegacyForgeNavigationClient.acceptTarget(p.success, p.type, p.id, p.dimension, p.x, p.y, p.z, p.starting, p.message))); context.setPacketHandled(true); }
	}
	private record CancelRequest() {
		static void encode(CancelRequest p, FriendlyByteBuf b) { } static CancelRequest decode(FriendlyByteBuf b) { return new CancelRequest(); }
		static void handle(CancelRequest p, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender(); context.enqueueWork(() -> { if (player != null) LegacyForgeMod.navigation().cancel(player, "导航已取消，临时磁石与方向导航指南针已回收", true); }); context.setPacketHandled(true); }
	}
	private record StateResponse(boolean active, String message) {
		static void encode(StateResponse p, FriendlyByteBuf b) { b.writeBoolean(p.active); b.writeUtf(p.message, 300); } static StateResponse decode(FriendlyByteBuf b) { return new StateResponse(b.readBoolean(), b.readUtf(300)); }
		static void handle(StateResponse p, Supplier<NetworkEvent.Context> supplier) { NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LegacyForgeNavigationClient.acceptState(p.active, p.message))); context.setPacketHandled(true); }
	}
}
