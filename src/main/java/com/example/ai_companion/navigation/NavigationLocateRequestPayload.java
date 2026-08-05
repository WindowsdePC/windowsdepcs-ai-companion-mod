package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NavigationLocateRequestPayload(String targetType, String id, boolean teleport)
		implements CustomPacketPayload {
	public static final Type<NavigationLocateRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_locate_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationLocateRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationLocateRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationLocateRequestPayload(buffer.readUtf(NavigationEntry.MAX_TYPE_LENGTH),
					buffer.readUtf(NavigationEntry.MAX_ID_LENGTH), buffer.readBoolean());
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationLocateRequestPayload payload) {
				buffer.writeUtf(payload.targetType(), NavigationEntry.MAX_TYPE_LENGTH);
				buffer.writeUtf(payload.id(), NavigationEntry.MAX_ID_LENGTH);
				buffer.writeBoolean(payload.teleport());
			}
		};

	public NavigationLocateRequestPayload {
		new NavigationEntry(targetType, id);
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
