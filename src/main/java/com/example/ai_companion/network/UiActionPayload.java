package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** A bounded, typed UI request. It never contains a command line. */
public record UiActionPayload(String action, List<String> arguments) implements CustomPacketPayload {
	public static final int MAX_ARGUMENTS = 12;
	public static final int MAX_ARGUMENT_LENGTH = 6_000;
	public static final Type<UiActionPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "ui_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UiActionPayload> CODEC = new StreamCodec<>() {
		@Override
		public UiActionPayload decode(RegistryFriendlyByteBuf buffer) {
			String action = buffer.readUtf(64);
			int size = Math.clamp(buffer.readVarInt(), 0, MAX_ARGUMENTS);
			List<String> arguments = new ArrayList<>(size);
			for (int index = 0; index < size; index++) {
				arguments.add(buffer.readUtf(MAX_ARGUMENT_LENGTH));
			}
			return new UiActionPayload(action, arguments);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, UiActionPayload payload) {
			buffer.writeUtf(payload.action, 64);
			buffer.writeVarInt(payload.arguments.size());
			payload.arguments.forEach(value -> buffer.writeUtf(value, MAX_ARGUMENT_LENGTH));
		}
	};

	public UiActionPayload {
		action = action == null ? "" : action.strip();
		if (!action.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid UI action");
		arguments = List.copyOf(arguments == null ? List.of() : arguments);
		if (arguments.size() > MAX_ARGUMENTS
				|| arguments.stream().anyMatch(value -> value == null || value.length() > MAX_ARGUMENT_LENGTH)) {
			throw new IllegalArgumentException("Invalid UI action arguments");
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
