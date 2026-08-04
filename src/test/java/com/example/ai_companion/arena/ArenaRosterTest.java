package com.example.ai_companion.arena;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ArenaRosterTest {
	@Test
	void twoVersusTwoAssignsStableTeams() {
		ArenaRoster roster = ArenaRoster.create(ArenaMode.TWO_V_TWO,
			List.of("Red_A", "Red_B", "Blue_A", "Blue_B"));
		assertEquals(List.of("Red_A", "Red_B"), roster.membersOfTeam(0));
		assertEquals(List.of("Blue_A", "Blue_B"), roster.membersOfTeam(1));
		assertEquals(Set.of(0, 1), roster.activeTeams(Set.of("Red_A")));
		assertEquals(Set.of(1), roster.activeTeams(Set.of("Red_A", "Red_B")));
	}

	@Test
	void freeForAllGivesEveryAgentAnIndependentTeam() {
		ArenaRoster roster = ArenaRoster.create(ArenaMode.FREE_FOR_ALL,
			List.of("AI_1", "AI_2", "AI_3"));
		assertEquals(Set.of(0, 1, 2), roster.activeTeams(Set.of()));
		assertEquals(Set.of(2), roster.activeTeams(Set.of("AI_1", "AI_2")));
	}

	@Test
	void rejectsWrongCountsAndCaseInsensitiveDuplicates() {
		assertThrows(IllegalArgumentException.class,
			() -> ArenaRoster.create(ArenaMode.ONE_V_ONE, List.of("AI_1")));
		assertThrows(IllegalArgumentException.class,
			() -> ArenaRoster.create(ArenaMode.FREE_FOR_ALL,
				List.of("Agent_1", "agent_1", "Agent_2")));
	}
}
