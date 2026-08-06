package com.example.ai_companion.mixin;

import com.example.ai_companion.maid.MaidProgression;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Charges negative level operations as the points in levels 0..N instead of currentLevel-N. */
@Mixin(Player.class)
public abstract class PlayerExperienceMixin {
	@Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
	private void aiCompanion$frontLevelCost(int levels, CallbackInfo callback) {
		if (levels >= 0) return;
		int requested = Math.min(255, -levels);
		Player player = (Player) (Object) this;
		player.giveExperiencePoints(-MaidProgression.frontLevelPointCost(requested));
		callback.cancel();
	}
}
