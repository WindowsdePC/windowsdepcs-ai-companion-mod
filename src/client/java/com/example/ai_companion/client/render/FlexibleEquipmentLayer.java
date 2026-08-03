package com.example.ai_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;

/** Renders non-equippable item models on the body part represented by each armor slot. */
public final class FlexibleEquipmentLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public FlexibleEquipmentLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
			AvatarRenderState state, float yRot, float xRot) {
		FlexibleEquipmentRenderState extra = (FlexibleEquipmentRenderState) state;
		renderSlot(poseStack, collector, packedLight, state, extra, EquipmentSlot.HEAD);
		renderSlot(poseStack, collector, packedLight, state, extra, EquipmentSlot.CHEST);
		renderSlot(poseStack, collector, packedLight, state, extra, EquipmentSlot.LEGS);
		renderSlot(poseStack, collector, packedLight, state, extra, EquipmentSlot.FEET);
	}

	private void renderSlot(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
			AvatarRenderState state, FlexibleEquipmentRenderState extra, EquipmentSlot slot) {
		ItemStackRenderState item = extra.ai_companion$itemState(slot);
		if (item.isEmpty()) return;
		poseStack.pushPose();
		switch (slot) {
			case HEAD -> {
				getParentModel().head.translateAndRotate(poseStack);
				poseStack.translate(0.0F, -0.55F, 0.0F);
				poseStack.scale(0.72F, 0.72F, 0.72F);
			}
			case CHEST -> {
				getParentModel().body.translateAndRotate(poseStack);
				poseStack.translate(0.0F, 0.35F, -0.34F);
				poseStack.scale(0.70F, 0.70F, 0.70F);
			}
			case LEGS -> {
				getParentModel().body.translateAndRotate(poseStack);
				poseStack.translate(0.0F, 0.95F, 0.0F);
				poseStack.scale(0.62F, 0.62F, 0.62F);
			}
			case FEET -> {
				getParentModel().rightLeg.translateAndRotate(poseStack);
				poseStack.translate(-0.12F, 0.78F, 0.0F);
				poseStack.scale(0.55F, 0.55F, 0.55F);
			}
			default -> {
				poseStack.popPose();
				return;
			}
		}
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		item.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
