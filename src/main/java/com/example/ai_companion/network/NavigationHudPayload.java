package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.exploration.NavigationMode;
import com.example.ai_companion.exploration.NavigationSnapshot;
import com.example.ai_companion.exploration.NavigationTargetType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded server-to-client AR navigation update. */
public record NavigationHudPayload(NavigationSnapshot snapshot) implements CustomPacketPayload {
	private static final int MAX_TEXT = 160;
	public static final Type<NavigationHudPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationHudPayload> CODEC =
		new StreamCodec<>() {
			@Override
			public NavigationHudPayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationHudPayload(new NavigationSnapshot(
					buffer.readBoolean(), NavigationMode.values()[boundedOrdinal(buffer.readVarInt(), NavigationMode.values().length)],
					NavigationTargetType.values()[boundedOrdinal(buffer.readVarInt(), NavigationTargetType.values().length)],
					buffer.readUtf(MAX_TEXT), buffer.readUtf(MAX_TEXT), buffer.readDouble(),
					buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
					buffer.readFloat(), buffer.readDouble()));
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, NavigationHudPayload payload) {
				NavigationSnapshot value = payload.snapshot();
				buffer.writeBoolean(value.active());
				buffer.writeVarInt(value.mode().ordinal());
				buffer.writeVarInt(value.targetType().ordinal());
				buffer.writeUtf(value.targetId(), MAX_TEXT);
				buffer.writeUtf(value.dimension(), MAX_TEXT);
				buffer.writeDouble(value.x());
				buffer.writeDouble(value.y());
				buffer.writeDouble(value.z());
				buffer.writeDouble(value.distance());
				buffer.writeDouble(value.initialDistance());
				buffer.writeFloat(value.relativeBearing());
				buffer.writeDouble(value.verticalDifference());
			}
		};

	private static int boundedOrdinal(int value, int length) {
		if (value < 0 || value >= length) throw new IllegalArgumentException("无效导航枚举值: " + value);
		return value;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
