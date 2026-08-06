package com.example.ai_companion.society;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.List;

/** Server-authoritative entry point for home, job, economy and social state. */
public final class SocietyManager implements AutoCloseable {
	private final AgentManager agents;
	private final SocietyStore store = SocietyStore.load();

	public SocietyManager(AgentManager agents) { this.agents = agents; }
	public SocietyResident enroll(String agent) throws IOException { return store.enroll(agents.canonicalName(agent)); }
	public SocietyResident status(String agent) { return store.require(agent); }
	public List<SocietyResident> residents() { return store.residents(); }

	public SocietyResident setHome(String agent, String dimension, double x, double y, double z) throws IOException {
		SocietyResident updated = store.require(agent).withHome(dimension, x, y, z); store.put(updated); return updated;
	}

	public SocietyResident setJob(String agent, SocietyJob job) throws IOException {
		SocietyResident updated = store.require(agent).withJob(job); store.put(updated); return updated;
	}

	public SocietyResident work(String agent, long now) throws IOException {
		SocietyResident updated = store.require(agent).work(now); store.put(updated); return updated;
	}

	public int socialize(String first, String second) throws IOException {
		SocietyResident left = store.require(first), right = store.require(second);
		if (left.agentName().equalsIgnoreCase(right.agentName())) throw new IllegalArgumentException("需要两名不同的 AI 社会成员");
		left = left.relate(right.agentName(), 5); right = right.relate(left.agentName(), 5);
		store.put(left); store.put(right); return left.relationshipWith(right.agentName());
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 0) return;
		try { store.processDay(server.overworld().getDayTime() / 24_000L); }
		catch (IOException error) { AiCompanionMod.LOGGER.error("Cannot process AI society day", error); }
	}

	@Override public void close() { store.close(); }
}
