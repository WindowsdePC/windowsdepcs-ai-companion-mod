package com.example.ai_companion.gameplay;

import net.minecraft.world.entity.LivingEntity;

import java.util.function.BooleanSupplier;

/** Side-aware state used by the common armor-slot mixin. */
public final class FlexibleEquipmentMode {
	private static BooleanSupplier serverEnabled = () -> false;
	private static BooleanSupplier clientEnabled = () -> false;

	private FlexibleEquipmentMode() {
	}

	public static void configureServer(BooleanSupplier enabled) {
		serverEnabled = enabled;
	}

	public static void configureClient(BooleanSupplier enabled) {
		clientEnabled = enabled;
	}

	public static boolean isEnabledFor(LivingEntity owner) {
		return owner.level().isClientSide() ? clientEnabled.getAsBoolean() : serverEnabled.getAsBoolean();
	}
}
