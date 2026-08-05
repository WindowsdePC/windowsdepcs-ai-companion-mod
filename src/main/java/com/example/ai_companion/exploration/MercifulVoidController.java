package com.example.ai_companion.exploration;

import com.example.ai_companion.config.GameplayConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Rescues players below the dimension floor and returns them gently to safe terrain. */
public final class MercifulVoidController implements AutoCloseable {
	private static final int RESCUE_COOLDOWN_TICKS = 100;
	private static final int SLOW_FALL_TICKS = 20 * 45;
	private final Supplier<GameplayConfig> config;
	private final Map<UUID, Integer> nextRescueTick = new HashMap<>();

	public MercifulVoidController(Supplier<GameplayConfig> config) {
		this.config = config;
	}

	public synchronized void tick(MinecraftServer server) {
		if (!config.get().mercifulVoidEnabled()) return;
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.getY() >= player.level().getMinY() - 24.0
					|| now < nextRescueTick.getOrDefault(player.getUUID(), 0)) continue;
			rescue(player, now);
		}
	}

	private void rescue(ServerPlayer player, int now) {
		ServerLevel level = player.level();
		int x = MthFloor.floor(player.getX());
		int z = MthFloor.floor(player.getZ());
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (ground <= level.getMinY() + 1) {
			// Dimension-safe fallback: overworld spawn terrain, the End main island and the
			// Nether all generate around the dimension origin without requiring spawn API coupling.
			x = 0;
			z = 0;
			ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		}
		int maximum = level.getMinY() + level.getHeight() - 4;
		double rescueY = Math.clamp(ground + 28.0, level.getMinY() + 8.0, maximum);
		player.teleportTo(x + 0.5, rescueY, z + 0.5);
		player.setDeltaMovement(new Vec3(0.0, -0.08, 0.0));
		player.fallDistance = 0.0F;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0,
			false, false, true));
		player.sendOverlayMessage(Component.literal("仁慈的虚空已将你送回高空；缓降期间不会受到摔落伤害"));
		nextRescueTick.put(player.getUUID(), now + RESCUE_COOLDOWN_TICKS);
	}

	/** Keeps floor conversion isolated for tests and mapping upgrades. */
	private static final class MthFloor {
		private MthFloor() { }

		static int floor(double value) {
			int integer = (int) value;
			return value < integer ? integer - 1 : integer;
		}
	}

	@Override
	public synchronized void close() {
		nextRescueTick.clear();
	}
}
