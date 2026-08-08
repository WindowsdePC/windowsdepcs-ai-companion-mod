package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the server-authoritative non-secret API configuration. */
public record ApiConfigRequestPayload() implements CustomPacketPayload {
	public static final Type<ApiConfigRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "request_api_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ApiConfigRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override public ApiConfigRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new ApiConfigRequestPayload();
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, ApiConfigRequestPayload payload) { }
		};

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
