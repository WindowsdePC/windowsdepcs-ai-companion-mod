package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server status used to stop the HUD only after destination cleanup has completed. */
public record NavigationStatePayload(boolean active, String message) implements CustomPacketPayload {
	public static final int MAX_MESSAGE_LENGTH = 300;
	public static final Type<NavigationStatePayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationStatePayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationStatePayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationStatePayload(buffer.readBoolean(), buffer.readUtf(MAX_MESSAGE_LENGTH));
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationStatePayload payload) {
				buffer.writeBoolean(payload.active());
				buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
			}
		};

	public NavigationStatePayload {
		message = message == null ? "" : message;
		if (message.length() > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("Navigation message too long");
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
