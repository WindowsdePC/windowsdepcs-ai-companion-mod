package com.example.ai_companion.pet;

import java.util.SplittableRandom;

/** Pure scoring rules shared by commands and automated tests. */
public final class PetCompetitionEngine {
	private PetCompetitionEngine() { }

	public static Result compete(PetCompetitionMode mode, PetProfile first, PetProfile second, long seed) {
		if (first.name().equalsIgnoreCase(second.name())) {
			throw new IllegalArgumentException("参赛宠物不能相同");
		}
		SplittableRandom random = new SplittableRandom(seed);
		int firstScore = baseScore(mode, first) + random.nextInt(61);
		int secondScore = baseScore(mode, second) + random.nextInt(61);
		if (firstScore == secondScore) {
			firstScore += first.endurance() >= second.endurance() ? 1 : 0;
			secondScore += first.endurance() < second.endurance() ? 1 : 0;
		}
		PetProfile winner = firstScore > secondScore ? first : second;
		PetProfile loser = winner == first ? second : first;
		return new Result(mode, winner, loser, Math.max(firstScore, secondScore), Math.min(firstScore, secondScore));
	}

	private static int baseScore(PetCompetitionMode mode, PetProfile pet) {
		return switch (mode) {
			case RACE -> pet.speed() * 5 + pet.endurance() * 3 + pet.strength();
			case BATTLE -> pet.strength() * 5 + pet.endurance() * 3 + pet.speed();
		};
	}

	public record Result(PetCompetitionMode mode, PetProfile winner, PetProfile loser,
			int winnerScore, int loserScore) { }
}
