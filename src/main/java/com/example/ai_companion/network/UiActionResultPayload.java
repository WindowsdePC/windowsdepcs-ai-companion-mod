package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded server-to-client UI result. Keeps button feedback inside mod screens instead of chat. */
public record UiActionResultPayload(boolean success, String message) implements CustomPacketPayload {
	public static final int MAX_MESSAGE_LENGTH = 2_048;
	public static final Type<UiActionResultPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "ui_action_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UiActionResultPayload> CODEC = new StreamCodec<>() {
		@Override public UiActionResultPayload decode(RegistryFriendlyByteBuf buffer) {
			return new UiActionResultPayload(buffer.readBoolean(), buffer.readUtf(MAX_MESSAGE_LENGTH));
		}

		@Override public void encode(RegistryFriendlyByteBuf buffer, UiActionResultPayload payload) {
			buffer.writeBoolean(payload.success());
			buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
		}
	};

	public UiActionResultPayload {
		message = message == null ? "" : message.strip();
		if (message.length() > MAX_MESSAGE_LENGTH) message = message.substring(0, MAX_MESSAGE_LENGTH);
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
