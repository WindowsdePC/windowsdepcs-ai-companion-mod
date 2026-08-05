package com.example.ai_companion.client.mixin;

import com.example.ai_companion.client.render.FlexibleEquipmentLayer;
import com.example.ai_companion.client.render.FlexibleEquipmentRenderState;
import com.example.ai_companion.client.ClientPerformanceController;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds one item-model render layer while leaving vanilla armor and wearable rendering untouched. */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin
		extends LivingEntityRenderer<Avatar, AvatarRenderState, PlayerModel> {
	protected AvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model,
			float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void ai_companion$addFlexibleEquipmentLayer(EntityRendererProvider.Context context,
			boolean slim, CallbackInfo callback) {
		addLayer(new FlexibleEquipmentLayer(this));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
		+ "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void ai_companion$extractFlexibleEquipment(Avatar avatar, AvatarRenderState state,
			float partialTick, CallbackInfo callback) {
		FlexibleEquipmentRenderState extra = (FlexibleEquipmentRenderState) state;
		if (!ClientPerformanceController.shouldRenderExtra(avatar)) {
			extra.ai_companion$itemState(EquipmentSlot.HEAD).clear();
			extra.ai_companion$itemState(EquipmentSlot.CHEST).clear();
			extra.ai_companion$itemState(EquipmentSlot.LEGS).clear();
			extra.ai_companion$itemState(EquipmentSlot.FEET).clear();
			return;
		}
		ai_companion$resolve(extra.ai_companion$itemState(EquipmentSlot.HEAD), state.headEquipment,
			ItemDisplayContext.HEAD, avatar);
		ai_companion$resolve(extra.ai_companion$itemState(EquipmentSlot.CHEST), state.chestEquipment,
			ItemDisplayContext.FIXED, avatar);
		ai_companion$resolve(extra.ai_companion$itemState(EquipmentSlot.LEGS), state.legsEquipment,
			ItemDisplayContext.FIXED, avatar);
		ai_companion$resolve(extra.ai_companion$itemState(EquipmentSlot.FEET), state.feetEquipment,
			ItemDisplayContext.FIXED, avatar);
	}

	@Unique
	private void ai_companion$resolve(ItemStackRenderState target, ItemStack stack,
			ItemDisplayContext context,
			Avatar avatar) {
		target.clear();
		if (stack.isEmpty() || stack.get(DataComponents.EQUIPPABLE) != null) return;
		itemModelResolver.updateForLiving(target, stack, context, avatar);
	}
}
