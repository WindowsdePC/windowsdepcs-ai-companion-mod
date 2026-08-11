package com.example.ai_companion.legacy;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/** Fabric fake player that is registered through the vanilla PlayerList lifecycle. */
final class LegacyVisibleFakePlayer extends FakePlayer {
	LegacyVisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level, profile);
	}

	/** Fabric 1.20.1's utility FakePlayer has an empty tick; use vanilla player lifecycle here. */
	@Override public void tick() { super.tick(); }

	/** Fabric 1.20.1's utility FakePlayer is otherwise unconditionally invulnerable. */
	@Override public boolean isInvulnerableTo(DamageSource source) {
		return super.isInvulnerableTo(source);
	}
}
