package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** A real vanilla ServerPlayer lifecycle controlled by the mod and tracked by normal clients. */
final class VisibleFakePlayer extends ServerPlayer implements ManagedAiAvatar {
	VisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level.getServer(), level, profile, ClientInformation.createDefault());
	}
}
