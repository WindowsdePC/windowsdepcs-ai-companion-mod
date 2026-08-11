package com.example.ai_companion.agent;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/** Fabric fake player registered through the vanilla player-list lifecycle so clients can track it. */
final class VisibleFakePlayer extends FakePlayer {
	VisibleFakePlayer(ServerLevel level, GameProfile profile) {
		super(level, profile);
	}

	/**
	 * Fabric's utility fake player deliberately has an empty tick and is always invulnerable. Those
	 * defaults are useful for short-lived automation tools, but are wrong for a persistent AI avatar.
	 * Running the vanilla player tick restores fire, drowning, death and portal processing.
	 */
	@Override
	public void tick() {
		super.tick();
	}

	/** Use the normal ServerPlayer damage rules, including the currently selected game mode. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return super.isInvulnerableTo(level, source);
	}
}
