package com.example.ai_companion.society;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Atomic JSON persistence for the AI society. */
public final class SocietyStore implements AutoCloseable {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Map<String, SocietyProfile>>() { }.getType();
	private final Path path;
	private final Map<String, SocietyProfile> profiles;

	public static SocietyStore load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("ai_companion-society.json");
		try { return load(path); }
		catch (IOException | RuntimeException error) {
			AiCompanionMod.LOGGER.error("Cannot load AI society from {}", path, error);
			return new SocietyStore(path, new LinkedHashMap<>());
		}
	}

	static SocietyStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new SocietyStore(path, new LinkedHashMap<>());
		Map<String, SocietyProfile> loaded = GSON.fromJson(Files.readString(path), DATA_TYPE);
		Map<String, SocietyProfile> normalized = new LinkedHashMap<>();
		if (loaded != null) loaded.values().forEach(profile -> normalized.put(key(profile.agentName()), profile));
		return new SocietyStore(path, normalized);
	}

	SocietyStore(Path path, Map<String, SocietyProfile> profiles) {
		this.path = path;
		this.profiles = profiles;
	}

	public synchronized SocietyProfile enroll(String agentName) throws IOException {
		String key = key(agentName);
		if (profiles.containsKey(key)) throw new IllegalStateException("AI 已加入模拟社会");
		if (profiles.size() >= 128) throw new IllegalStateException("模拟社会最多容纳 128 名 AI");
		SocietyProfile created = SocietyProfile.enroll(agentName);
		profiles.put(key, created); save(); return created;
	}

	public synchronized SocietyProfile require(String name) {
		SocietyProfile profile = profiles.get(key(name));
		if (profile == null) throw new IllegalArgumentException("AI 尚未加入模拟社会：" + name);
		return profile;
	}

	public synchronized void update(SocietyProfile profile) throws IOException {
		profiles.put(key(profile.agentName()), profile); save();
	}

	public synchronized void updateBoth(SocietyProfile first, SocietyProfile second) throws IOException {
		profiles.put(key(first.agentName()), first); profiles.put(key(second.agentName()), second); save();
	}

	public synchronized List<SocietyProfile> leaderboard() {
		return profiles.values().stream().sorted(Comparator.comparingLong(SocietyProfile::balance).reversed()
			.thenComparing(Comparator.comparingInt(SocietyProfile::reputation).reversed())
			.thenComparing(SocietyProfile::agentName, String.CASE_INSENSITIVE_ORDER)).limit(10).toList();
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporary, GSON.toJson(profiles, DATA_TYPE), StandardCharsets.UTF_8);
		try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
		catch (IOException atomicFailure) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
	}

	private static String key(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("AI 名称不能为空");
		return value.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	public synchronized void close() {
		try { save(); }
		catch (IOException error) { AiCompanionMod.LOGGER.error("Cannot save AI society to {}", path, error); }
	}
}
