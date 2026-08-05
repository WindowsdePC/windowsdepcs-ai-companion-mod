package com.example.ai_companion.cooperation;

import java.util.Map;

/** Pure majority rules shared by persistent collaboration groups and tests. */
public final class CollaborationRules {
	private CollaborationRules() {}

	public enum Outcome { PENDING, APPROVED, REJECTED }

	public static Outcome proposalOutcome(int memberCount, Map<String, Boolean> votes) {
		if (memberCount < 2) throw new IllegalArgumentException("A group needs at least two members");
		long approvals = votes.values().stream().filter(Boolean::booleanValue).count();
		long rejections = votes.size() - approvals;
		if (approvals > memberCount / 2) return Outcome.APPROVED;
		if (rejections > memberCount / 2) return Outcome.REJECTED;
		return Outcome.PENDING;
	}

	public static String electedLeader(int memberCount, Map<String, String> votes) {
		if (memberCount < 2) throw new IllegalArgumentException("A group needs at least two members");
		return votes.values().stream()
			.collect(java.util.stream.Collectors.groupingBy(value -> value,
				java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
			.entrySet().stream()
			.filter(entry -> entry.getValue() > memberCount / 2)
			.map(Map.Entry::getKey)
			.findFirst().orElse("");
	}
}
