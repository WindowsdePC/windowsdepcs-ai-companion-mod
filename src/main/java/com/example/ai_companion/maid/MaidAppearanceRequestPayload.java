package com.example.ai_companion.maid;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MaidAppearanceRequestPayload() implements CustomPacketPayload {
	public static final Type<MaidAppearanceRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "request_maid_appearances"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MaidAppearanceRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override public MaidAppearanceRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new MaidAppearanceRequestPayload();
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, MaidAppearanceRequestPayload value) { }
		};
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
