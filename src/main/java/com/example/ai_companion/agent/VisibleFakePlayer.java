package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;

/** Fabric fake player registered through the vanilla player-list lifecycle so clients can track it. */
final class VisibleFakePlayer extends FakePlayer {
	VisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level, profile);
	}
}
