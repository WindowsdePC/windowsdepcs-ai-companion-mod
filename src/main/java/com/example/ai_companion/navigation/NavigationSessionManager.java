package com.example.ai_companion.navigation;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Owns temporary lodestones, bound compasses and arrival cleanup for active routes. */
public final class NavigationSessionManager implements AutoCloseable {
	private static final String ITEM_MARKER = "ai_companion_navigation_owner";
	private static final int ARRIVAL_RADIUS = 8;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private MinecraftServer server;

	public synchronized StartedRoute begin(ServerPlayer player, String type, String id,
			ServerLevel targetLevel, BlockPos locatedTarget) {
		server = player.level().getServer();
		cancel(player, "已替换上一条导航路线", false);
		BlockPos lodestone = placeTemporaryLodestone(player, targetLevel, locatedTarget);
		BlockState previous = targetLevel.getBlockState(lodestone);
		if (!targetLevel.setBlockAndUpdate(lodestone, Blocks.LODESTONE.defaultBlockState())) {
			throw new IllegalStateException("无法在目标附近生成临时磁石");
		}
		double startingDistance = player.level() == targetLevel
			? NavigationMath.horizontalDistance(player.getX(), player.getZ(), lodestone.getX(), lodestone.getZ()) : -1;
		ItemStack compass = navigationCompass(player, targetLevel, lodestone);
		if (!player.getInventory().add(compass)) {
			player.drop(compass, false, true);
		} else {
			int slot = player.getInventory().findSlotMatchingItem(compass);
			if (slot >= 0) player.getInventory().pickSlot(slot);
		}
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges();
		sessions.put(player.getUUID(), new Session(player.getUUID(), type, id, targetLevel.dimension(),
			lodestone, previous, startingDistance));
		return new StartedRoute(lodestone, startingDistance);
	}

	public synchronized void tick(MinecraftServer currentServer) {
		server = currentServer;
		for (Session session : new ArrayList<>(sessions.values())) {
			ServerPlayer player = currentServer.getPlayerList().getPlayer(session.playerId());
			if (player == null) continue;
			ServerLevel level = currentServer.getLevel(session.dimension());
			if (level == null || !level.getBlockState(session.lodestone()).is(Blocks.LODESTONE)) {
				cancel(player, "导航磁石已不存在，路线已取消", true);
				continue;
			}
			if (player.level() != level) continue;
			double distance = NavigationMath.horizontalDistance(player.getX(), player.getZ(),
				session.lodestone().getX(), session.lodestone().getZ());
			if (distance <= ARRIVAL_RADIUS) {
				cancel(player, "已抵达“" + session.id() + "”，临时磁石与方向导航指南针已回收", true);
			}
		}
	}

	public synchronized boolean cancel(ServerPlayer player, String message, boolean notify) {
		Session removed = sessions.remove(player.getUUID());
		removeNavigationCompasses(player);
		if (removed != null) restoreLodestone(removed);
		if (notify) {
			ServerPlayNetworking.send(player, new NavigationStatePayload(false, message));
			player.sendSystemMessage(Component.literal(message));
		}
		return removed != null;
	}

	public synchronized void disconnect(ServerPlayer player) {
		cancel(player, "", false);
	}

	@Override public synchronized void close() {
		if (server != null) {
			for (Session session : new ArrayList<>(sessions.values())) {
				ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
				if (player != null) removeNavigationCompasses(player);
				restoreLodestone(session);
			}
		}
		sessions.clear();
		server = null;
	}

	private BlockPos placeTemporaryLodestone(ServerPlayer player, ServerLevel level, BlockPos target) {
		for (int attempt = 0; attempt < 32; attempt++) {
			int radius = attempt == 0 ? 0 : 3 + player.getRandom().nextInt(10);
			int x = target.getX() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius);
			int z = target.getZ() + (radius == 0 ? 0 : player.getRandom().nextInt(radius * 2 + 1) - radius);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos candidate = new BlockPos(x, y, z);
			if (level.getBlockState(candidate).canBeReplaced()) return candidate;
		}
		throw new IllegalStateException("目标附近没有可安全放置临时磁石的位置");
	}

	private static ItemStack navigationCompass(ServerPlayer owner, ServerLevel level, BlockPos lodestone) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("方向导航"));
		stack.set(DataComponents.LODESTONE_TRACKER,
			new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), lodestone)), true));
		CompoundTag tag = new CompoundTag();
		tag.putString(ITEM_MARKER, owner.getUUID().toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static void removeNavigationCompasses(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.COMPASS)) continue;
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null || !player.getUUID().toString().equals(
					data.copyTag().getStringOr(ITEM_MARKER, ""))) continue;
			player.getInventory().setItem(slot, ItemStack.EMPTY);
		}
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges();
	}

	private void restoreLodestone(Session session) {
		if (server == null) return;
		ServerLevel level = server.getLevel(session.dimension());
		if (level != null && level.getBlockState(session.lodestone()).is(Blocks.LODESTONE)) {
			level.setBlockAndUpdate(session.lodestone(), session.previousState());
		}
	}

	public record StartedRoute(BlockPos target, double startingDistance) { }
	private record Session(UUID playerId, String type, String id,
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
			BlockPos lodestone, BlockState previousState, double startingDistance) { }
}
