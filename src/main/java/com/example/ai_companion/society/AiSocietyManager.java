package com.example.ai_companion.society;

import com.example.ai_companion.agent.AgentManager;

import java.io.IOException;
import java.util.List;

/** Server-authoritative service for AI homes, jobs, economy and relationships. */
public final class AiSocietyManager implements AutoCloseable {
	private final AgentManager agents;
	private final SocietyStore store;

	public AiSocietyManager(AgentManager agents) { this(agents, SocietyStore.load()); }
	AiSocietyManager(AgentManager agents, SocietyStore store) { this.agents = agents; this.store = store; }

	public SocietyProfile enroll(String agentName) throws IOException {
		String canonical = agents.canonicalName(agentName);
		return store.enroll(canonical);
	}

	public SocietyProfile setHome(String agentName, String dimension, double x, double y, double z) throws IOException {
		SocietyProfile updated = store.require(agentName).withHome(dimension, x, y, z);
		store.update(updated); return updated;
	}

	public SocietyProfile setJob(String agentName, SocietyJob job) throws IOException {
		SocietyProfile updated = store.require(agentName).withJob(job);
		store.update(updated); return updated;
	}

	public SocietyProfile work(String agentName, long nowMillis) throws IOException {
		SocietyProfile updated = store.require(agentName).work(nowMillis);
		store.update(updated); return updated;
	}

	public SocietyProfile rest(String agentName) throws IOException {
		SocietyProfile updated = store.require(agentName).rest();
		store.update(updated); return updated;
	}

	public SocialResult socialize(String firstName, String secondName) throws IOException {
		SocietyProfile first = store.require(firstName);
		SocietyProfile second = store.require(secondName);
		SocietyProfile nextFirst = first.relate(second.agentName(), 5);
		SocietyProfile nextSecond = second.relate(first.agentName(), 5);
		store.updateBoth(nextFirst, nextSecond);
		return new SocialResult(nextFirst, nextSecond, nextFirst.relationWith(second.agentName()));
	}

	public TradeResult trade(String sellerName, String buyerName, long amount) throws IOException {
		if (amount < 1 || amount > 1_000_000) throw new IllegalArgumentException("交易金额必须为 1～1000000");
		SocietyProfile seller = store.require(sellerName);
		SocietyProfile buyer = store.require(buyerName);
		if (seller.agentName().equalsIgnoreCase(buyer.agentName())) throw new IllegalArgumentException("交易双方不能相同");
		SocietyProfile nextSeller = seller.transfer(-amount);
		SocietyProfile nextBuyer = buyer.transfer(amount);
		store.updateBoth(nextSeller, nextBuyer);
		return new TradeResult(nextSeller, nextBuyer, amount);
	}

	public SocietyProfile status(String agentName) { return store.require(agentName); }
	public List<SocietyProfile> leaderboard() { return store.leaderboard(); }
	@Override public void close() { store.close(); }

	public record SocialResult(SocietyProfile first, SocietyProfile second, int relationship) { }
	public record TradeResult(SocietyProfile seller, SocietyProfile buyer, long amount) { }
}
