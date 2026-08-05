package com.example.ai_companion.exploration;

import com.example.ai_companion.config.GameplayConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Reversibly expands the vanilla world border to the engine's hard coordinate envelope. */
public final class WorldLimitController implements AutoCloseable {
	private static final double MAX_BORDER_SIZE = 59_999_968.0;

	private record BorderSnapshot(double centerX, double centerZ, double size,
			double safeZone, double damagePerBlock, int warningBlocks, int warningTime) { }

	private final Supplier<GameplayConfig> config;
	private final Map<WorldBorder, BorderSnapshot> previousBorders = new IdentityHashMap<>();
	private boolean applied;

	public WorldLimitController(Supplier<GameplayConfig> config) {
		this.config = config;
	}

	public synchronized void tick(MinecraftServer server) {
		boolean enabled = config.get().worldLimitsRemoved();
		if (enabled) {
			for (ServerLevel level : server.getAllLevels()) expand(level.getWorldBorder());
			applied = true;
		} else if (applied) {
			restore();
		}
	}

	private void expand(WorldBorder border) {
		previousBorders.computeIfAbsent(border, value -> new BorderSnapshot(value.getCenterX(),
			value.getCenterZ(), value.getSize(), value.getSafeZone(), value.getDamagePerBlock(),
			value.getWarningBlocks(), value.getWarningTime()));
		if (border.getSize() != MAX_BORDER_SIZE) border.setSize(MAX_BORDER_SIZE);
		if (border.getCenterX() != 0.0 || border.getCenterZ() != 0.0) border.setCenter(0.0, 0.0);
		if (border.getSafeZone() != MAX_BORDER_SIZE) border.setSafeZone(MAX_BORDER_SIZE);
		if (border.getDamagePerBlock() != 0.0) border.setDamagePerBlock(0.0);
		if (border.getWarningBlocks() != 0) border.setWarningBlocks(0);
		if (border.getWarningTime() != 0) border.setWarningTime(0);
	}

	private void restore() {
		for (Map.Entry<WorldBorder, BorderSnapshot> entry : previousBorders.entrySet()) {
			WorldBorder border = entry.getKey();
			BorderSnapshot old = entry.getValue();
			border.setCenter(old.centerX, old.centerZ);
			border.setSize(old.size);
			border.setSafeZone(old.safeZone);
			border.setDamagePerBlock(old.damagePerBlock);
			border.setWarningBlocks(old.warningBlocks);
			border.setWarningTime(old.warningTime);
		}
		previousBorders.clear();
		applied = false;
	}

	@Override
	public synchronized void close() {
		restore();
	}
}
