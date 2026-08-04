package com.example.ai_companion.network;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.agent.AgentPosition;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.List;

/** Registers the common position payload types and the server request handler. */
public final class AgentPositionNetworking {
	private AgentPositionNetworking() {
	}

	public static void registerServer(AgentManager agents) {
		PayloadTypeRegistry.serverboundPlay().register(
			AgentPositionRequestPayload.TYPE, AgentPositionRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
			AgentPositionsPayload.TYPE, AgentPositionsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AgentPositionRequestPayload.TYPE, (payload, context) -> {
			List<AgentPosition> snapshot = agents.positions().stream()
				.limit(AgentPositionsPayload.MAX_POSITIONS)
				.toList();
			context.responseSender().sendPacket(new AgentPositionsPayload(snapshot));
		});
	}
}
