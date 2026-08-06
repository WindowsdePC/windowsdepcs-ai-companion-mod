package com.example.ai_companion.pet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Coordinates persistence and competition result accounting. */
public final class PetCompetitionManager implements AutoCloseable {
	private final PetCompetitionStore store = PetCompetitionStore.load();

	public PetProfile create(UUID owner, String name) throws IOException { return store.create(owner, name); }
	public List<PetProfile> list(UUID owner) { return store.list(owner); }
	public PetProfile status(UUID owner, String name) { return store.require(owner, name); }

	public PetProfile train(UUID owner, String name, PetAttribute attribute, long now) throws IOException {
		PetProfile trained = store.require(owner, name).train(attribute, now);
		store.put(trained);
		return trained;
	}

	public PetCompetitionRules.Result race(UUID owner, String names, long seed) throws IOException {
		var unique = new java.util.LinkedHashMap<String, PetProfile>();
		Arrays.stream(names.split(",")).map(String::strip).filter(name -> !name.isBlank())
			.forEach(name -> unique.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), store.require(owner, name)));
		List<PetProfile> entries = List.copyOf(unique.values());
		PetCompetitionRules.Result result = PetCompetitionRules.race(entries, seed);
		for (PetProfile profile : entries) store.put(profile.recordRace(profile.name().equals(result.winner())));
		return result;
	}

	public PetCompetitionRules.Result battle(UUID owner, String first, String second, long seed) throws IOException {
		PetProfile left = store.require(owner, first);
		PetProfile right = store.require(owner, second);
		PetCompetitionRules.Result result = PetCompetitionRules.battle(left, right, seed);
		store.put(left.recordBattle(left.name().equals(result.winner())));
		store.put(right.recordBattle(right.name().equals(result.winner())));
		return result;
	}

	@Override public void close() { store.close(); }
}
