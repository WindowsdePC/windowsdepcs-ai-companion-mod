package com.example.ai_companion.cooperation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CollaborationRulesTest {
	@Test void proposalNeedsStrictMajority() {
		Map<String, Boolean> votes = new LinkedHashMap<>();
		votes.put("a", true);
		votes.put("b", true);
		assertEquals(CollaborationRules.Outcome.PENDING,
			CollaborationRules.proposalOutcome(4, votes));
		votes.put("c", true);
		assertEquals(CollaborationRules.Outcome.APPROVED,
			CollaborationRules.proposalOutcome(4, votes));
	}

	@Test void rejectionAndLeaderElectionNeedStrictMajority() {
		assertEquals(CollaborationRules.Outcome.REJECTED,
			CollaborationRules.proposalOutcome(3, Map.of("a", false, "b", false)));
		assertEquals("", CollaborationRules.electedLeader(4, Map.of("a", "Alex", "b", "Alex")));
		assertEquals("Alex", CollaborationRules.electedLeader(4,
			Map.of("a", "Alex", "b", "Alex", "c", "Alex")));
	}
}
