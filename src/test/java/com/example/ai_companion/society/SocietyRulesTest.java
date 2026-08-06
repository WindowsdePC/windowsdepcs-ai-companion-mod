package com.example.ai_companion.society;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SocietyRulesTest {
	@Test void workIncomeIsBoundedAndRewardsExperience() {
		assertEquals(0, SocietyRules.workIncome(SocietyJob.UNEMPLOYED, 100));
		assertEquals(8, SocietyRules.workIncome(SocietyJob.FARMER, 0));
		assertEquals(18, SocietyRules.workIncome(SocietyJob.FARMER, 1_000));
	}

	@Test void relationshipsAndBalanceStayBounded() {
		assertEquals(100, SocietyRules.relationship(99, 50));
		assertEquals(-100, SocietyRules.relationship(-90, -50));
		assertEquals(0, SocietyRules.dailyBalance(0, SocietyJob.UNEMPLOYED, true, 0));
		assertTrue(SocietyRules.dailyBalance(20, SocietyJob.TRADER, true, 0) > 20);
	}

	@Test void homeJobEconomyAndFriendsImproveWellbeing() {
		int basic = SocietyRules.wellbeing(false, SocietyJob.UNEMPLOYED, 0, Map.of());
		int settled = SocietyRules.wellbeing(true, SocietyJob.BUILDER, 200, Map.of("Alex", 80));
		assertTrue(settled > basic);
		assertTrue(settled <= 100);
	}
}
