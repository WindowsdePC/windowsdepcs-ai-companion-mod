package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

import java.util.Set;

/** Creates a fake player through PlayerList instead of adding an untracked world entity. */
final class VisibleAgentSpawner {
	private VisibleAgentSpawner() {
	}

	static VisibleFakePlayer spawnNear(ServerPlayer owner, GameProfile profile, int ordinal) {
		VisibleFakePlayer player = new VisibleFakePlayer(owner.level(), profile);
		// Creation is deliberately anchored to the player who requested this AI. Do not choose a
		// random point in a radius: the caller must immediately be able to see the new player.
		Vec3 position = owner.position();
		return register(owner.level().getServer(), owner.level(), player, position,
			owner.getYRot(), owner.getXRot());
	}

	static VisibleFakePlayer restore(MinecraftServer server, ServerLevel level, GameProfile profile,
			double x, double y, double z) {
		VisibleFakePlayer player = new VisibleFakePlayer(level, profile);
		return register(server, level, player, new Vec3(x, y, z), 0.0F, 0.0F);
	}

	private static VisibleFakePlayer register(MinecraftServer server, ServerLevel level,
			VisibleFakePlayer player, Vec3 position, float yaw, float pitch) {
		if (server.getPlayerList().getPlayer(player.getUUID()) != null) {
			throw new IllegalStateException("相同 UUID 的 AI 玩家已经在线");
		}
		player.setPos(position.x, position.y, position.z);
		server.getPlayerList().placeNewPlayer(new SilentAiConnection(), player,
			new CommonListenerCookie(player.getGameProfile(), 0, player.clientInformation(), false));
		player.teleportTo(level, position.x, position.y, position.z, Set.of(), yaw, pitch, true);
		player.setGameMode(GameType.SURVIVAL);
		player.setInvulnerable(false);
		player.getAbilities().invulnerable = false;
		player.getAbilities().flying = false;
		player.getAbilities().mayfly = false;
		player.setHealth(player.getMaxHealth());
		player.setCustomNameVisible(true);
		if (server.getPlayerList().getPlayer(player.getUUID()) != player || level.getEntity(player.getUUID()) != player) {
			server.getPlayerList().remove(player);
			throw new IllegalStateException("AI 玩家未能加入玩家列表或当前世界");
		}
		return player;
	}
}
