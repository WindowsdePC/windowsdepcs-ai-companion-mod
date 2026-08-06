package com.example.ai_companion.pet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetCompetitionTest {
	@Test
	void enforcesInitialBudgetAndTrainingCooldown() {
		assertThrows(IllegalArgumentException.class,
			() -> PetProfile.create(UUID.randomUUID(), "Owner", "Overpowered", 100, 100, 100));
		PetProfile pet = PetProfile.create(UUID.randomUUID(), "Owner", "Runner", 70, 50, 60);
		PetProfile trained = pet.train(PetAttribute.SPEED, 30_000L);
		assertEquals(71, trained.speed());
		assertThrows(IllegalStateException.class, () -> trained.train(PetAttribute.SPEED, 59_999L));
	}

	@Test
	void raceAndBattleUseDifferentPrimaryAttributes() {
		PetProfile fast = PetProfile.create(UUID.randomUUID(), "A", "Fast", 100, 20, 50);
		PetProfile strong = PetProfile.create(UUID.randomUUID(), "B", "Strong", 20, 100, 50);
		assertEquals("Fast", PetCompetitionEngine.compete(PetCompetitionMode.RACE, fast, strong, 7).winner().name());
		assertEquals("Strong", PetCompetitionEngine.compete(PetCompetitionMode.BATTLE, fast, strong, 7).winner().name());
	}
}
