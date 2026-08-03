package com.example.ai_companion.mixin;

import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows every item in armor slots only while the opt-in mode is enabled. */
@Mixin(ArmorSlot.class)
abstract class ArmorSlotMixin {
	@Shadow @Final private LivingEntity owner;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void ai_companion$allowAnyItem(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
		if (FlexibleEquipmentMode.isEnabledFor(owner)) result.setReturnValue(true);
	}
}
