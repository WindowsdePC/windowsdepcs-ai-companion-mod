package com.example.ai_companion.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.Entity;

/** Reuses vanilla's glowing-outline render path while F3+B is enabled. */
public final class F3BHighlightController {
	private static volatile ClientSettings settings;

	private F3BHighlightController() {
	}

	public static void initialize(ClientSettings clientSettings) {
		settings = clientSettings;
	}

	public static boolean replacesHitboxes(Minecraft minecraft) {
		ClientSettings current = settings;
		return current != null && current.f3BGlowingHitboxesEnabled
			&& minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
	}

	public static boolean shouldGlow(Minecraft minecraft, Entity entity) {
		if (!replacesHitboxes(minecraft) || entity.isInvisible()) return false;
		return entity != minecraft.getCameraEntity()
			|| minecraft.options.getCameraType() != CameraType.FIRST_PERSON;
	}
}
