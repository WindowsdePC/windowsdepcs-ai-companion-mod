package com.example.ai_companion.society;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Atomic JSON persistence for at most 128 simulated AI residents. */
public final class SocietyStore implements AutoCloseable {
	public static final int MAX_RESIDENTS = 128;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final class FileData { long lastDay = -1; Map<String, SocietyResident> residents = new LinkedHashMap<>(); }

	private final Path path;
	private final Map<String, SocietyResident> residents;
	private long lastDay;

	public static SocietyStore load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("windowsdepcs-ai-companion-society.json");
		try { return load(path); }
		catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty AI society", path, error);
			return new SocietyStore(path, -1, new LinkedHashMap<>());
		}
	}

	static SocietyStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new SocietyStore(path, -1, new LinkedHashMap<>());
		FileData root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<String, SocietyResident> data = root == null || root.residents == null
			? new LinkedHashMap<>() : new LinkedHashMap<>();
		if (root != null && root.residents != null) root.residents.values().stream().filter(java.util.Objects::nonNull)
			.limit(MAX_RESIDENTS).forEach(value -> data.put(key(value.agentName()), value));
		return new SocietyStore(path, root == null ? -1 : root.lastDay, data);
	}

	private SocietyStore(Path path, long lastDay, Map<String, SocietyResident> residents) {
		this.path = path; this.lastDay = Math.max(-1, lastDay); this.residents = residents;
	}

	public synchronized SocietyResident enroll(String canonicalName) throws IOException {
		String key = key(canonicalName);
		if (residents.containsKey(key)) throw new IllegalArgumentException("AI 已加入社会：" + canonicalName);
		if (residents.size() >= MAX_RESIDENTS) throw new IllegalStateException("社会成员已达到 128 名上限");
		SocietyResident resident = SocietyResident.enroll(canonicalName);
		residents.put(key, resident); save(); return resident;
	}

	public synchronized SocietyResident require(String name) {
		SocietyResident resident = residents.get(key(name));
		if (resident == null) throw new IllegalArgumentException("AI 尚未加入社会：" + name);
		return resident;
	}

	public synchronized List<SocietyResident> residents() { return List.copyOf(residents.values()); }
	public synchronized void put(SocietyResident resident) throws IOException { residents.put(key(resident.agentName()), resident); save(); }

	public synchronized boolean processDay(long day) throws IOException {
		if (day <= lastDay) return false;
		if (lastDay >= 0) residents.replaceAll((ignored, resident) -> resident.dayCycle());
		lastDay = day; save(); return true;
	}

	private static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData root = new FileData(); root.lastDay = lastDay; root.residents = new LinkedHashMap<>(residents);
		Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
		catch (IOException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
	}
	@Override public synchronized void close() {
		try { save(); } catch (IOException error) { AiCompanionMod.LOGGER.error("Cannot save AI society", error); }
	}
}
