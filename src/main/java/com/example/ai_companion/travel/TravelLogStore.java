package com.example.ai_companion.travel;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded JSON persistence for per-player travel journals. */
public final class TravelLogStore implements AutoCloseable {
	public static final int MAX_ENTRIES_PER_PLAYER = 512;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class PlayerJournal {
		List<TravelLogEntry> entries = new ArrayList<>();
		long nextEntryId = 1;
	}

	private static final class FileData {
		Map<String, PlayerJournal> journals = new LinkedHashMap<>();
	}

	private final Path path;
	private final Map<String, PlayerJournal> journals;

	public static TravelLogStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-travel-journals.json");
		try {
			return load(path);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty travel journals", path, error);
			return new TravelLogStore(path, new LinkedHashMap<>());
		}
	}

	static TravelLogStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new TravelLogStore(path, new LinkedHashMap<>());
		FileData loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<String, PlayerJournal> data = loaded == null || loaded.journals == null
			? new LinkedHashMap<>() : loaded.journals;
		data.values().forEach(TravelLogStore::normalize);
		return new TravelLogStore(path, data);
	}

	private TravelLogStore(Path path, Map<String, PlayerJournal> journals) {
		this.path = path;
		this.journals = journals;
	}

	public synchronized TravelLogEntry addIfAbsent(UUID playerId, TravelLogCategory category,
			String discoveryKey, String name, String dimension, double x, double y, double z,
			long discoveredAtEpochMillis) throws IOException {
		PlayerJournal journal = journal(playerId);
		for (TravelLogEntry entry : journal.entries) {
			if (entry.discoveryKey().equals(discoveryKey)) return null;
		}
		if (journal.entries.size() >= MAX_ENTRIES_PER_PLAYER) {
			throw new IllegalStateException("旅行图鉴已满；每位玩家最多记录 "
				+ MAX_ENTRIES_PER_PLAYER + " 个地点");
		}
		TravelLogEntry entry = new TravelLogEntry(journal.nextEntryId++, category, discoveryKey,
			name, dimension, x, y, z, discoveredAtEpochMillis, 0);
		journal.entries.add(entry);
		save();
		return entry;
	}

	public synchronized List<TravelLogEntry> entries(UUID playerId) {
		return journal(playerId).entries.stream()
			.sorted(Comparator.comparingLong(TravelLogEntry::id).reversed()).toList();
	}

	public synchronized TravelLogEntry require(UUID playerId, long id) {
		return journal(playerId).entries.stream().filter(entry -> entry.id() == id).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("找不到旅行日志 #" + id));
	}

	public synchronized TravelLogEntry linkPhoto(UUID playerId, long entryId, long photoId)
			throws IOException {
		PlayerJournal journal = journal(playerId);
		for (int index = 0; index < journal.entries.size(); index++) {
			TravelLogEntry entry = journal.entries.get(index);
			if (entry.id() != entryId) continue;
			TravelLogEntry updated = entry.withPhoto(photoId);
			journal.entries.set(index, updated);
			save();
			return updated;
		}
		throw new IllegalArgumentException("找不到旅行日志 #" + entryId);
	}

	public synchronized Map<TravelLogCategory, Long> categoryCounts(UUID playerId) {
		Map<TravelLogCategory, Long> counts = new LinkedHashMap<>();
		for (TravelLogCategory category : TravelLogCategory.values()) counts.put(category, 0L);
		for (TravelLogEntry entry : journal(playerId).entries) {
			counts.compute(entry.category(), (ignored, count) -> count == null ? 1L : count + 1L);
		}
		return Map.copyOf(counts);
	}

	private PlayerJournal journal(UUID playerId) {
		return journals.computeIfAbsent(playerId.toString(), ignored -> new PlayerJournal());
	}

	private static void normalize(PlayerJournal journal) {
		if (journal.entries == null) journal.entries = new ArrayList<>();
		journal.entries = new ArrayList<>(journal.entries.stream()
			.filter(java.util.Objects::nonNull).limit(MAX_ENTRIES_PER_PLAYER).toList());
		long next = journal.entries.stream().mapToLong(TravelLogEntry::id).max().orElse(0) + 1;
		journal.nextEntryId = Math.max(Math.max(1, journal.nextEntryId), next);
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData root = new FileData();
		root.journals = journals;
		Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException unsupportedAtomicMove) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Override
	public synchronized void close() {
		try {
			save();
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save travel journals to {}", path, error);
		}
	}
}
