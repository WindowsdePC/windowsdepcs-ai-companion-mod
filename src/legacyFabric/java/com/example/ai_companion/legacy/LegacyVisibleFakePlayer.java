package com.example.ai_companion.legacy;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;

/** Fabric fake player that is registered through the vanilla PlayerList lifecycle. */
final class LegacyVisibleFakePlayer extends FakePlayer {
	LegacyVisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level, profile);
	}
}
