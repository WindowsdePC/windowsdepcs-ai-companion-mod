package com.example.ai_companion.pet;

import java.util.Comparator;
import java.util.List;

/** Deterministic bounded competition rules, isolated from Minecraft for testing. */
public final class PetCompetitionRules {
	private PetCompetitionRules() {}

	public record Result(String winner, List<String> ranking, int rounds) {}

	public static Result race(List<PetProfile> pets, long seed) {
		if (pets == null || pets.size() < 2 || pets.size() > 8) {
			throw new IllegalArgumentException("竞速需要 2～8 只宠物");
		}
		List<String> ranking = pets.stream()
			.sorted(Comparator.<PetProfile>comparingInt(pet -> raceScore(pet, seed)).reversed()
				.thenComparing(PetProfile::name))
			.map(PetProfile::name).toList();
		return new Result(ranking.getFirst(), ranking, 1);
	}

	public static Result battle(PetProfile first, PetProfile second, long seed) {
		if (first == null || second == null || first.name().equalsIgnoreCase(second.name())) {
			throw new IllegalArgumentException("战斗需要两只不同的宠物");
		}
		int firstHealth = 80 + first.endurance() * 2;
		int secondHealth = 80 + second.endurance() * 2;
		int rounds = 0;
		boolean firstActs = first.speed() + jitter(seed, first.name(), 9)
			>= second.speed() + jitter(seed, second.name(), 9);
		while (firstHealth > 0 && secondHealth > 0 && rounds < 100) {
			rounds++;
			if (firstActs) {
				secondHealth -= damage(first, second, seed + rounds);
				if (secondHealth > 0) firstHealth -= damage(second, first, seed - rounds);
			} else {
				firstHealth -= damage(second, first, seed + rounds);
				if (firstHealth > 0) secondHealth -= damage(first, second, seed - rounds);
			}
		}
		String winner = firstHealth == secondHealth
			? (first.name().compareToIgnoreCase(second.name()) <= 0 ? first.name() : second.name())
			: (firstHealth > secondHealth ? first.name() : second.name());
		String loser = winner.equals(first.name()) ? second.name() : first.name();
		return new Result(winner, List.of(winner, loser), rounds);
	}

	private static int raceScore(PetProfile pet, long seed) {
		return pet.speed() * 5 + pet.endurance() * 3 + pet.strength()
			+ jitter(seed, pet.name(), 41);
	}

	private static int damage(PetProfile attacker, PetProfile defender, long seed) {
		return Math.max(1, 4 + attacker.strength() / 8 + attacker.speed() / 20
			- defender.endurance() / 25 + jitter(seed, attacker.name(), 5));
	}

	private static int jitter(long seed, String name, int bound) {
		long mixed = seed ^ (long) name.toLowerCase().hashCode() * 0x9E3779B97F4A7C15L;
		mixed ^= mixed >>> 33;
		mixed *= 0xff51afd7ed558ccdL;
		mixed ^= mixed >>> 33;
		return Math.floorMod((int) mixed, Math.max(1, bound));
	}
}
