package com.example.ai_companion.gameplay;

import com.example.ai_companion.config.GameplayConfig;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Gives an unenchanted golden spear a configurable Lunge-II-like attack impulse. */
public final class GoldenSpearRush {
	private final Supplier<GameplayConfig> config;
	private final Map<UUID, Integer> rushCounts = new HashMap<>();

	public GoldenSpearRush(Supplier<GameplayConfig> config) {
		this.config = config;
	}

	public void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
				|| !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			GameplayConfig current = config.get();
			ItemStack spear = player.getItemInHand(hand);
			if (!current.goldenSpearRushEnabled() || !spear.is(Items.GOLDEN_SPEAR)) {
				return InteractionResult.PASS;
			}

			Vec3 look = player.getLookAngle();
			player.push(look.x * current.rushStrength(), 0, look.z * current.rushStrength());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LUNGE_2,
				SoundSource.PLAYERS, 1.0F, 1.0F);

			int count = rushCounts.merge(player.getUUID(), 1, Integer::sum);
			if (!player.getAbilities().instabuild) {
				if (count % current.durabilityEvery() == 0) {
					spear.hurtAndBreak(1, (ServerLevel) level, serverPlayer, item -> { });
				}
				if (current.hungerCost() > 0 && count % current.hungerEvery() == 0) {
					int food = player.getFoodData().getFoodLevel();
					player.getFoodData().setFoodLevel(Math.max(0, food - current.hungerCost()));
				}
			}
			return InteractionResult.PASS;
		});
	}

	public void clearCounters() {
		rushCounts.clear();
	}
}
