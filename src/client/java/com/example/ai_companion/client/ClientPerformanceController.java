package com.example.ai_companion.client;

import com.example.ai_companion.util.AdaptiveRenderDistance;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Limits only the optional render work introduced by this mod; vanilla render distance is untouched. */
public final class ClientPerformanceController {
	private static volatile ClientSettings settings;
	private static volatile int effectiveDistance = 96;
	private static volatile double smoothedFps = 60.0;
	private static long previousFrameNanos;
	private static int framesUntilAdjustment = 60;

	private ClientPerformanceController() {
	}

	public static void initialize(ClientSettings clientSettings) {
		settings = clientSettings;
		effectiveDistance = clientSettings.extraRenderDistance;
		LevelRenderEvents.END_MAIN.register(context -> sampleFrame());
	}

	private static void sampleFrame() {
		long now = System.nanoTime();
		if (previousFrameNanos != 0L) {
			double elapsed = (now - previousFrameNanos) / 1_000_000_000.0;
			if (elapsed > 0.0 && elapsed < 1.0) {
				double fps = 1.0 / elapsed;
				smoothedFps = smoothedFps * 0.92 + fps * 0.08;
			}
		}
		previousFrameNanos = now;

		ClientSettings current = settings;
		if (current == null || !current.clientPerformanceOptimizerEnabled) {
			effectiveDistance = current == null ? 96 : current.extraRenderDistance;
			framesUntilAdjustment = 60;
			return;
		}
		if (!current.adaptiveExtraRenderDistance) {
			effectiveDistance = current.extraRenderDistance;
			framesUntilAdjustment = 60;
			return;
		}
		if (--framesUntilAdjustment <= 0) {
			effectiveDistance = AdaptiveRenderDistance.next(effectiveDistance,
				current.minimumExtraRenderDistance, current.extraRenderDistance,
				current.performanceTargetFps, smoothedFps);
			framesUntilAdjustment = 60;
		}
	}

	public static boolean shouldRenderExtra(Entity entity) {
		ClientSettings current = settings;
		if (current == null || !current.clientPerformanceOptimizerEnabled) return true;
		Entity camera = Minecraft.getInstance().getCameraEntity();
		if (camera == null || camera == entity) return true;
		double distance = effectiveDistance;
		return camera.distanceToSqr(entity) <= distance * distance;
	}

	public static String statusText() {
		ClientSettings current = settings;
		if (current == null || !current.clientPerformanceOptimizerEnabled) return "优化器关闭";
		String mode = current.adaptiveExtraRenderDistance ? "自适应" : "固定";
		return mode + " · 当前附加渲染距离 " + effectiveDistance + " 格 · 估算 "
			+ Math.round(smoothedFps) + " FPS";
	}
}
