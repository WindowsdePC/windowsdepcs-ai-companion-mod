package com.example.ai_companion.pet;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Atomic JSON persistence for AI pet profiles. */
public final class PetCompetitionStore implements AutoCloseable {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Map<String, PetProfile>>() { }.getType();
	private final Path path;
	private final Map<String, PetProfile> pets;

	public static PetCompetitionStore load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("ai_companion-pets.json");
		try {
			return load(path);
		} catch (IOException | RuntimeException error) {
			AiCompanionMod.LOGGER.error("Cannot load AI pet profiles from {}", path, error);
			return new PetCompetitionStore(path, new LinkedHashMap<>());
		}
	}

	static PetCompetitionStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new PetCompetitionStore(path, new LinkedHashMap<>());
		Map<String, PetProfile> loaded = GSON.fromJson(Files.readString(path), DATA_TYPE);
		Map<String, PetProfile> normalized = new LinkedHashMap<>();
		if (loaded != null) loaded.values().forEach(pet -> normalized.put(key(pet.name()), pet));
		return new PetCompetitionStore(path, normalized);
	}

	PetCompetitionStore(Path path, Map<String, PetProfile> pets) {
		this.path = path;
		this.pets = pets;
	}

	public synchronized PetProfile create(UUID ownerId, String ownerName, String name,
			int speed, int strength, int endurance) throws IOException {
		String key = key(name);
		if (pets.containsKey(key)) throw new IllegalStateException("宠物名称已存在：" + name);
		if (pets.values().stream().filter(pet -> pet.ownerId().equals(ownerId)).count() >= 8) {
			throw new IllegalStateException("每位玩家最多拥有 8 只竞技宠物");
		}
		PetProfile created = PetProfile.create(ownerId, ownerName, name, speed, strength, endurance);
		pets.put(key, created);
		save();
		return created;
	}

	public synchronized PetProfile require(String name) {
		PetProfile pet = pets.get(key(name));
		if (pet == null) throw new IllegalArgumentException("未找到宠物：" + name);
		return pet;
	}

	public synchronized void update(PetProfile pet) throws IOException {
		pets.put(key(pet.name()), pet);
		save();
	}

	public synchronized List<PetProfile> ownedBy(UUID ownerId) {
		return pets.values().stream().filter(pet -> pet.ownerId().equals(ownerId))
			.sorted(Comparator.comparing(PetProfile::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	public synchronized List<PetProfile> leaderboard() {
		return pets.values().stream().sorted(Comparator.comparingInt(PetProfile::rating).reversed()
			.thenComparing(PetProfile::name, String.CASE_INSENSITIVE_ORDER)).limit(10).toList();
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporary, GSON.toJson(pets, DATA_TYPE), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicFailure) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String key(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("宠物名称不能为空");
		return value.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	public synchronized void close() {
		try {
			save();
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save AI pet profiles to {}", path, error);
		}
	}
}
