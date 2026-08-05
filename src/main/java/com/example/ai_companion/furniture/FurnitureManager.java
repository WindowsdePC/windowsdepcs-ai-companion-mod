package com.example.ai_companion.furniture;

import com.example.ai_companion.agent.AgentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.function.Consumer;

/** Connects registered furniture to managed AI player leisure actions. */
public final class FurnitureManager {
	private static final int SEARCH_RADIUS = 8;
	private final AgentManager agents;

	public FurnitureManager(AgentManager agents) {
		this.agents = agents;
	}

	public BlockPos sitNearest(ServerPlayer owner, String agentName) {
		BlockPos origin = owner.blockPosition();
		BlockPos sofa = BlockPos.betweenClosedStream(origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
			origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))
			.filter(position -> owner.level().getBlockState(position).is(FurnitureBlocks.SOFA))
			.min(Comparator.comparingDouble(position -> position.distSqr(origin)))
			.map(BlockPos::immutable)
			.orElseThrow(() -> new IllegalStateException("附近 8 格内没有沙发"));
		agents.seatAtFurniture(agentName, owner.level(), sofa);
		return sofa;
	}

	public void stand(String agentName) {
		agents.standFromFurniture(agentName);
	}

	public void chat(MinecraftServer server, String agentName, String message, Consumer<String> result) {
		if (!agents.isSeatedAtFurniture(agentName)) {
			throw new IllegalStateException("该 AI 尚未坐在沙发上");
		}
		String safe = message == null ? "" : message.strip();
		if (safe.isBlank() || safe.length() > 400) {
			throw new IllegalArgumentException("聊天内容长度必须为 1～400 字符");
		}
		agents.ask(server, agentName,
			"你正在家具休闲区坐着聊天。请回应玩家这句话，不要移动：" + safe, result);
	}
}
