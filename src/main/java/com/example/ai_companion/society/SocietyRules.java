package com.example.ai_companion.society;

import java.util.Map;

/** Pure economy, relationship and wellbeing rules shared with automated tests. */
public final class SocietyRules {
	private SocietyRules() {}

	public static int workIncome(SocietyJob job, int completedWorkCycles) {
		if (job == null || job == SocietyJob.UNEMPLOYED) return 0;
		return job.baseWage() + Math.min(10, Math.max(0, completedWorkCycles) / 5);
	}

	public static int relationship(int current, int change) {
		return Math.max(-100, Math.min(100, current + change));
	}

	public static int dailyBalance(int current, SocietyJob job, boolean hasHome, int completedWorkCycles) {
		int livingCost = hasHome ? 2 : 1;
		int income = workIncome(job, completedWorkCycles) / 2;
		return Math.max(0, Math.min(1_000_000, current + income - livingCost));
	}

	public static int wellbeing(boolean hasHome, SocietyJob job, int balance, Map<String, Integer> relationships) {
		int relationshipAverage = relationships == null || relationships.isEmpty() ? 0
			: (int) relationships.values().stream().mapToInt(Integer::intValue).average().orElse(0);
		return Math.max(0, Math.min(100, 30 + (hasHome ? 20 : 0)
			+ (job == null || job == SocietyJob.UNEMPLOYED ? 0 : 20)
			+ Math.min(20, Math.max(0, balance / 10)) + relationshipAverage / 10));
	}
}
