package com.example.ai_companion.pet;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Atomic JSON persistence for bounded player-owned pet rosters. */
public final class PetCompetitionStore implements AutoCloseable {
	public static final int MAX_PETS_PER_PLAYER = 8;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final class FileData { Map<String, PetProfile> pets = new LinkedHashMap<>(); }

	private final Path path;
	private final Map<String, PetProfile> pets;

	public static PetCompetitionStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-pets.json");
		try { return load(path); }
		catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty pet roster", path, error);
			return new PetCompetitionStore(path, new LinkedHashMap<>());
		}
	}

	static PetCompetitionStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new PetCompetitionStore(path, new LinkedHashMap<>());
		FileData root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<String, PetProfile> loaded = root == null || root.pets == null
			? new LinkedHashMap<>() : new LinkedHashMap<>(root.pets);
		loaded.entrySet().removeIf(entry -> entry.getValue() == null);
		return new PetCompetitionStore(path, loaded);
	}

	private PetCompetitionStore(Path path, Map<String, PetProfile> pets) {
		this.path = path;
		this.pets = pets;
	}

	public synchronized PetProfile create(UUID owner, String name) throws IOException {
		if (list(owner).size() >= MAX_PETS_PER_PLAYER) throw new IllegalStateException("每位玩家最多拥有 8 只竞技宠物");
		String key = key(owner, name);
		if (pets.containsKey(key)) throw new IllegalArgumentException("同名宠物已存在：" + name);
		PetProfile profile = PetProfile.create(owner, name);
		pets.put(key, profile);
		save();
		return profile;
	}

	public synchronized PetProfile require(UUID owner, String name) {
		PetProfile profile = pets.get(key(owner, name));
		if (profile == null) throw new IllegalArgumentException("找不到你的宠物：" + name);
		return profile;
	}

	public synchronized List<PetProfile> list(UUID owner) {
		return pets.values().stream().filter(pet -> pet.ownerId().equals(owner))
			.sorted(java.util.Comparator.comparing(PetProfile::name)).toList();
	}

	public synchronized void put(PetProfile profile) throws IOException {
		pets.put(key(profile.ownerId(), profile.name()), profile);
		save();
	}

	private static String key(UUID owner, String name) {
		return owner + ":" + (name == null ? "" : name.strip().toLowerCase(Locale.ROOT));
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData root = new FileData();
		root.pets = new LinkedHashMap<>(pets);
		Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
		catch (IOException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
	}

	@Override public synchronized void close() {
		try { save(); }
		catch (IOException error) { AiCompanionMod.LOGGER.error("Cannot save pet roster to {}", path, error); }
	}
}
