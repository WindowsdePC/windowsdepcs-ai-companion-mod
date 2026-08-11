package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests server-authoritative cleanup of the caller's active navigation session. */
public record NavigationCancelRequestPayload() implements CustomPacketPayload {
	public static final Type<NavigationCancelRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_cancel_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationCancelRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationCancelRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationCancelRequestPayload();
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationCancelRequestPayload payload) { }
		};

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
