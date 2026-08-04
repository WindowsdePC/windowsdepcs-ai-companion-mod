package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Empty client-to-server request for a fresh AI position snapshot. */
public record AgentPositionRequestPayload() implements CustomPacketPayload {
	public static final Type<AgentPositionRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "request_agent_positions"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AgentPositionRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override
			public AgentPositionRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new AgentPositionRequestPayload();
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, AgentPositionRequestPayload payload) {
				// The request intentionally has no body.
			}
		};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
