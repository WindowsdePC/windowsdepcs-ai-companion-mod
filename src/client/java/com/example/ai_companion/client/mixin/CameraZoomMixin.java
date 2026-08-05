package com.example.ai_companion.client.mixin;

import com.example.ai_companion.client.ScreenZoomController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraZoomMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void ai_companion$applyScreenZoom(float partialTicks,
			CallbackInfoReturnable<Float> result) {
		result.setReturnValue(ScreenZoomController.apply(result.getReturnValue()));
	}
}
