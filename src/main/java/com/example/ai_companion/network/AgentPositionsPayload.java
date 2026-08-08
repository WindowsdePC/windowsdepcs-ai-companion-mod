package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentPosition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Bounded server-to-client snapshot used by the hold-to-view position HUD. */
public record AgentPositionsPayload(List<AgentPosition> positions) implements CustomPacketPayload {
	public static final int MAX_POSITIONS = 128;
	private static final int MAX_NAME_LENGTH = 16;
	private static final int MAX_DIMENSION_LENGTH = 128;
	private static final int MAX_MODE_LENGTH = 24;
	private static final int MAX_MESSAGE_LENGTH = 512;

	public static final Type<AgentPositionsPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "agent_positions"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AgentPositionsPayload> CODEC =
		new StreamCodec<>() {
			@Override
			public AgentPositionsPayload decode(RegistryFriendlyByteBuf buffer) {
				int count = buffer.readVarInt();
				if (count < 0 || count > MAX_POSITIONS) {
					throw new IllegalArgumentException("Invalid AI position count: " + count);
				}
				List<AgentPosition> positions = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					positions.add(new AgentPosition(
						buffer.readUtf(MAX_NAME_LENGTH),
						buffer.readUtf(MAX_DIMENSION_LENGTH),
						buffer.readDouble(),
						buffer.readDouble(),
						buffer.readDouble(),
						com.example.ai_companion.agent.AgentMode.valueOf(buffer.readUtf(MAX_MODE_LENGTH)),
						buffer.readUtf(MAX_MESSAGE_LENGTH)));
				}
				return new AgentPositionsPayload(positions);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, AgentPositionsPayload payload) {
				if (payload.positions().size() > MAX_POSITIONS) {
					throw new IllegalArgumentException("Too many AI positions: " + payload.positions().size());
				}
				buffer.writeVarInt(payload.positions().size());
				for (AgentPosition position : payload.positions()) {
					buffer.writeUtf(position.name(), MAX_NAME_LENGTH);
					buffer.writeUtf(position.dimension(), MAX_DIMENSION_LENGTH);
					buffer.writeDouble(position.x());
					buffer.writeDouble(position.y());
					buffer.writeDouble(position.z());
					buffer.writeUtf(position.mode().name(), MAX_MODE_LENGTH);
					buffer.writeUtf(position.lastMessage(), MAX_MESSAGE_LENGTH);
				}
			}
		};

	public AgentPositionsPayload {
		positions = List.copyOf(positions);
		if (positions.size() > MAX_POSITIONS) {
			throw new IllegalArgumentException("Too many AI positions: " + positions.size());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
