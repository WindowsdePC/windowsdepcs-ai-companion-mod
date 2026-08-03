package com.example.ai_companion.client.render;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/** Extra item-model state attached to each player render-state instance by Mixin. */
public interface FlexibleEquipmentRenderState {
	ItemStackRenderState ai_companion$itemState(EquipmentSlot slot);
}
