package com.example.ai_companion.legacy;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-controlled player using the normal 1.20.1 damage, death and portal lifecycle. */
final class LegacyVisibleFakePlayer extends ServerPlayer {
	LegacyVisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level.getServer(), level, profile);
	}
}
