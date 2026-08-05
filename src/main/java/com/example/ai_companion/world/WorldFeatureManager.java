package com.example.ai_companion.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Applies the safe, reversible portion of the optional world-expansion features. */
public final class WorldFeatureManager implements AutoCloseable {
	public static final double BORDER_LANDS_COORDINATE = 12_550_821.0;
	private static final double MAXIMUM_BORDER_SIZE = 59_999_968.0;
	private static final int VOID_RESCUE_MARGIN = 48;
	private final Supplier<WorldFeatureConfig> config;
	private final Map<WorldBorder, BorderSnapshot> borders = new HashMap<>();
	private final Set<UUID> rescued = new HashSet<>();

	public WorldFeatureManager(Supplier<WorldFeatureConfig> config) {
		this.config = config;
	}

	public void tick(MinecraftServer server) {
		WorldFeatureConfig current = config.get();
		for (ServerLevel level : server.getAllLevels()) updateBorder(level.getWorldBorder(), current);
		if (!current.mercifulVoidEnabled()) {
			rescued.clear();
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (rescued.contains(player.getUUID())) {
				player.fallDistance = 0;
				if (player.onGround()) rescued.remove(player.getUUID());
			}
			if (player.getY() >= player.level().getMinY() - VOID_RESCUE_MARGIN) continue;
			rescue(player);
		}
	}

	private void updateBorder(WorldBorder border, WorldFeatureConfig current) {
		if (current.maximumWorldBorderEnabled()) {
			borders.computeIfAbsent(border, value -> new BorderSnapshot(value.getSize(),
				value.getDamagePerBlock(), value.getSafeZone()));
			if (border.getSize() != MAXIMUM_BORDER_SIZE) border.setSize(MAXIMUM_BORDER_SIZE);
			if (border.getDamagePerBlock() != 0) border.setDamagePerBlock(0);
			if (border.getSafeZone() < 64) border.setSafeZone(64);
		} else {
			BorderSnapshot previous = borders.remove(border);
			if (previous != null) previous.restore(border);
		}
	}

	private void rescue(ServerPlayer player) {
		ServerLevel level = player.level();
		int x = (int) Math.floor(player.getX());
		int z = (int) Math.floor(player.getZ());
		if (level.dimension() == Level.END) {
			x = 0;
			z = 0;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int minimumSafe = level.getMinY() + 96;
		int destinationY = Math.max(surface + 48, minimumSafe);
		player.teleportTo(x + 0.5, destinationY, z + 0.5);
		player.setDeltaMovement(0, 0, 0);
		player.fallDistance = 0;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 45, 0, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 50, 4, false, false));
		rescued.add(player.getUUID());
		player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
			"message.ai_companion.merciful_void"));
	}

	@Override
	public void close() {
		borders.forEach((border, snapshot) -> snapshot.restore(border));
		borders.clear();
		rescued.clear();
	}

	private record BorderSnapshot(double size, double damage, double safeZone) {
		void restore(WorldBorder border) {
			border.setSize(size);
			border.setDamagePerBlock(damage);
			border.setSafeZone(safeZone);
		}
	}
}
