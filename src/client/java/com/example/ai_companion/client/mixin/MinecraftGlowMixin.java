package com.example.ai_companion.client.mixin;

import com.example.ai_companion.client.F3BHighlightController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void aiCompanion$replaceHitboxWithOutline(Entity entity,
			CallbackInfoReturnable<Boolean> result) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (F3BHighlightController.shouldGlow(minecraft, entity)) {
			result.setReturnValue(true);
		}
	}
}
