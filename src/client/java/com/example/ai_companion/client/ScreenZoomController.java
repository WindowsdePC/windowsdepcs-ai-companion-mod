package com.example.ai_companion.client;

import com.example.ai_companion.util.SmoothZoomMath;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Applies a client-local, time-smoothed FOV multiplier while the configured key is held. */
public final class ScreenZoomController {
	private static volatile ClientSettings settings;
	private static double currentScale = 1.0;
	private static long previousFrameNanos;

	private ScreenZoomController() {
	}

	public static void initialize(ClientSettings clientSettings) {
		settings = clientSettings;
		currentScale = 1.0;
		previousFrameNanos = 0L;
	}

	public static float apply(float vanillaFov) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientSettings current = settings;
		long now = System.nanoTime();
		double elapsed = previousFrameNanos == 0L ? 0.0 : (now - previousFrameNanos) / 1_000_000_000.0;
		previousFrameNanos = now;

		boolean usable = current != null && current.screenZoomEnabled && minecraft.player != null
			&& minecraft.level != null && minecraft.gui.screen() == null;
		boolean held = usable && InputConstants.isKeyDown(minecraft.getWindow(), current.zoomCode());
		double target = held ? current.zoomFactor : 1.0;
		double transition = current == null ? 0.18 : current.zoomTransitionSeconds;
		currentScale = SmoothZoomMath.approach(currentScale, target, elapsed, transition);
		return (float) (vanillaFov / currentScale);
	}
}
