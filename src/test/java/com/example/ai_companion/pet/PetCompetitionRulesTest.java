package com.example.ai_companion.pet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PetCompetitionRulesTest {
	private static PetProfile pet(String name, int speed, int strength, int endurance) {
		return new PetProfile(UUID.randomUUID(), name, speed, strength, endurance, 0, 0, 0, 0, 0);
	}

	@Test void fastestWellConditionedPetWinsDeterministicRace() {
		var swift = pet("Swift", 100, 40, 100);
		var slow = pet("Slow", 1, 40, 1);
		var result = PetCompetitionRules.race(List.of(slow, swift), 42L);
		assertEquals("Swift", result.winner());
		assertEquals(List.of("Swift", "Slow"), result.ranking());
	}

	@Test void strongPetWinsBoundedBattle() {
		var strong = pet("Strong", 80, 100, 90);
		var weak = pet("Weak", 10, 1, 1);
		var result = PetCompetitionRules.battle(strong, weak, 7L);
		assertEquals("Strong", result.winner());
		assertTrue(result.rounds() > 0 && result.rounds() <= 100);
	}

	@Test void rejectsInvalidRosterAndEnforcesTrainingCooldown() {
		var pet = pet("Pet", 40, 40, 40);
		assertThrows(IllegalArgumentException.class, () -> PetCompetitionRules.race(List.of(pet), 1));
		var trained = pet.train(PetAttribute.SPEED, 40_000L);
		assertEquals(41, trained.speed());
		assertThrows(IllegalStateException.class, () -> trained.train(PetAttribute.SPEED, 50_000L));
	}
}
