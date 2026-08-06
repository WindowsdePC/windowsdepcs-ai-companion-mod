package com.example.ai_companion.pet;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Coordinates ownership, training and persistent competition results. */
public final class PetCompetitionManager implements AutoCloseable {
	private final PetCompetitionStore store;

	public PetCompetitionManager() {
		this(PetCompetitionStore.load());
	}

	PetCompetitionManager(PetCompetitionStore store) {
		this.store = store;
	}

	public PetProfile create(UUID ownerId, String ownerName, String name,
			int speed, int strength, int endurance) throws IOException {
		return store.create(ownerId, ownerName, name, speed, strength, endurance);
	}

	public PetProfile train(UUID ownerId, String name, PetAttribute attribute, long nowMillis) throws IOException {
		PetProfile pet = requireOwner(ownerId, name);
		PetProfile trained = pet.train(attribute, nowMillis);
		store.update(trained);
		return trained;
	}

	public PetCompetitionEngine.Result compete(PetCompetitionMode mode, String firstName,
			String secondName, long seed) throws IOException {
		PetProfile first = store.require(firstName);
		PetProfile second = store.require(secondName);
		PetCompetitionEngine.Result result = PetCompetitionEngine.compete(mode, first, second, seed);
		store.update(result.winner().recordResult(true));
		store.update(result.loser().recordResult(false));
		return result;
	}

	public List<PetProfile> ownedBy(UUID ownerId) { return store.ownedBy(ownerId); }
	public List<PetProfile> leaderboard() { return store.leaderboard(); }

	private PetProfile requireOwner(UUID ownerId, String name) {
		PetProfile pet = store.require(name);
		if (!pet.ownerId().equals(ownerId)) throw new IllegalStateException("只能训练自己的宠物");
		return pet;
	}

	@Override
	public void close() { store.close(); }
}
