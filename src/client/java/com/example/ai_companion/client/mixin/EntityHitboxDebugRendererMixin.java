package com.example.ai_companion.client.mixin;

import com.example.ai_companion.client.F3BHighlightController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
abstract class EntityHitboxDebugRendererMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
	private void aiCompanion$hideVanillaHitboxes(double cameraX, double cameraY, double cameraZ,
			DebugValueAccess debugValues, Frustum frustum, float partialTick, CallbackInfo callback) {
		if (F3BHighlightController.replacesHitboxes(minecraft)) {
			callback.cancel();
		}
	}
}
