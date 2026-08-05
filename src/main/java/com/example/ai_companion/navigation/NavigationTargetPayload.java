package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NavigationTargetPayload(boolean success, String targetType, String id, String dimension,
		double x, double y, double z, double startingDistance, String message)
		implements CustomPacketPayload {
	public static final int MAX_DIMENSION_LENGTH = 160;
	public static final int MAX_MESSAGE_LENGTH = 300;
	public static final Type<NavigationTargetPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_target"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationTargetPayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationTargetPayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationTargetPayload(buffer.readBoolean(),
					buffer.readUtf(NavigationEntry.MAX_TYPE_LENGTH), buffer.readUtf(NavigationEntry.MAX_ID_LENGTH),
					buffer.readUtf(MAX_DIMENSION_LENGTH), buffer.readDouble(), buffer.readDouble(),
					buffer.readDouble(), buffer.readDouble(), buffer.readUtf(MAX_MESSAGE_LENGTH));
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationTargetPayload payload) {
				buffer.writeBoolean(payload.success());
				buffer.writeUtf(payload.targetType(), NavigationEntry.MAX_TYPE_LENGTH);
				buffer.writeUtf(payload.id(), NavigationEntry.MAX_ID_LENGTH);
				buffer.writeUtf(payload.dimension(), MAX_DIMENSION_LENGTH);
				buffer.writeDouble(payload.x()); buffer.writeDouble(payload.y()); buffer.writeDouble(payload.z());
				buffer.writeDouble(payload.startingDistance());
				buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
			}
		};

	public NavigationTargetPayload {
		targetType = targetType == null ? "special" : targetType;
		id = id == null ? "" : id;
		dimension = dimension == null ? "" : dimension;
		message = message == null ? "" : message;
		if (targetType.length() > NavigationEntry.MAX_TYPE_LENGTH || id.length() > NavigationEntry.MAX_ID_LENGTH
				|| dimension.length() > MAX_DIMENSION_LENGTH || message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("Navigation target exceeds protocol bounds");
		}
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
