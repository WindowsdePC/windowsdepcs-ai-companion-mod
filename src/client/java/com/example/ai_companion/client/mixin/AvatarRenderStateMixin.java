package com.example.ai_companion.client.mixin;

import com.example.ai_companion.client.render.FlexibleEquipmentRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateMixin implements FlexibleEquipmentRenderState {
	@Unique private final ItemStackRenderState ai_companion$head = new ItemStackRenderState();
	@Unique private final ItemStackRenderState ai_companion$chest = new ItemStackRenderState();
	@Unique private final ItemStackRenderState ai_companion$legs = new ItemStackRenderState();
	@Unique private final ItemStackRenderState ai_companion$feet = new ItemStackRenderState();

	@Override
	public ItemStackRenderState ai_companion$itemState(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ai_companion$head;
			case CHEST -> ai_companion$chest;
			case LEGS -> ai_companion$legs;
			case FEET -> ai_companion$feet;
			default -> throw new IllegalArgumentException("Unsupported flexible equipment slot: " + slot);
		};
	}
}
