package com.example.ai_companion.orb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded JSON persistence for per-player assistant-orb waypoints and reminders. */
public final class AssistantOrbStore implements AutoCloseable {
	private static final int MAX_WAYPOINTS = 128;
	private static final int MAX_REMINDERS = 64;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class PlayerData {
		List<AssistantWaypoint> waypoints = new ArrayList<>();
		List<AssistantReminder> reminders = new ArrayList<>();
		long nextReminderId = 1;
	}

	private static final class FileData {
		Map<String, PlayerData> players = new LinkedHashMap<>();
	}

	public record DueReminder(UUID playerId, AssistantReminder reminder) {}

	private final Path path;
	private final Map<String, PlayerData> players;

	public static AssistantOrbStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-orbs.json");
		try {
			return load(path);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty assistant-orb data", path, error);
			return new AssistantOrbStore(path, new LinkedHashMap<>());
		}
	}

	static AssistantOrbStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new AssistantOrbStore(path, new LinkedHashMap<>());
		FileData loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<String, PlayerData> data = loaded == null || loaded.players == null
			? new LinkedHashMap<>() : loaded.players;
		data.values().forEach(AssistantOrbStore::normalize);
		return new AssistantOrbStore(path, data);
	}

	private AssistantOrbStore(Path path, Map<String, PlayerData> players) {
		this.path = path;
		this.players = players;
	}

	public synchronized AssistantWaypoint saveWaypoint(UUID playerId, AssistantWaypoint waypoint)
			throws IOException {
		PlayerData data = player(playerId);
		int existing = indexOfWaypoint(data, waypoint.name());
		if (existing < 0 && data.waypoints.size() >= MAX_WAYPOINTS) {
			throw new IllegalStateException("每位玩家最多保存 " + MAX_WAYPOINTS + " 个坐标");
		}
		if (existing >= 0) data.waypoints.set(existing, waypoint);
		else data.waypoints.add(waypoint);
		save();
		return waypoint;
	}

	public synchronized List<AssistantWaypoint> waypoints(UUID playerId) {
		return List.copyOf(player(playerId).waypoints);
	}

	public synchronized boolean removeWaypoint(UUID playerId, String name) throws IOException {
		PlayerData data = player(playerId);
		int index = indexOfWaypoint(data, name);
		if (index < 0) return false;
		data.waypoints.remove(index);
		save();
		return true;
	}

	public synchronized AssistantReminder addReminder(UUID playerId, long dueAtEpochMillis, String message)
			throws IOException {
		PlayerData data = player(playerId);
		if (data.reminders.size() >= MAX_REMINDERS) {
			throw new IllegalStateException("每位玩家最多保留 " + MAX_REMINDERS + " 个待触发提醒");
		}
		AssistantReminder reminder = new AssistantReminder(data.nextReminderId++, dueAtEpochMillis, message);
		data.reminders.add(reminder);
		save();
		return reminder;
	}

	public synchronized List<AssistantReminder> reminders(UUID playerId) {
		return player(playerId).reminders.stream()
			.sorted(Comparator.comparingLong(AssistantReminder::dueAtEpochMillis)).toList();
	}

	public synchronized boolean cancelReminder(UUID playerId, long id) throws IOException {
		boolean removed = player(playerId).reminders.removeIf(reminder -> reminder.id() == id);
		if (removed) save();
		return removed;
	}

	public synchronized List<DueReminder> takeDue(long nowEpochMillis, Collection<UUID> onlinePlayers)
			throws IOException {
		List<DueReminder> due = new ArrayList<>();
		for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
			UUID playerId;
			try {
				playerId = UUID.fromString(entry.getKey());
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			if (!onlinePlayers.contains(playerId)) continue;
			for (AssistantReminder reminder : entry.getValue().reminders) {
				if (reminder.dueAtEpochMillis() <= nowEpochMillis) due.add(new DueReminder(playerId, reminder));
			}
			entry.getValue().reminders.removeIf(reminder -> reminder.dueAtEpochMillis() <= nowEpochMillis);
		}
		if (!due.isEmpty()) save();
		return List.copyOf(due);
	}

	private PlayerData player(UUID playerId) {
		return players.computeIfAbsent(playerId.toString(), ignored -> new PlayerData());
	}

	private static int indexOfWaypoint(PlayerData data, String name) {
		for (int index = 0; index < data.waypoints.size(); index++) {
			if (data.waypoints.get(index).name().equalsIgnoreCase(name)) return index;
		}
		return -1;
	}

	private static void normalize(PlayerData data) {
		if (data.waypoints == null) data.waypoints = new ArrayList<>();
		if (data.reminders == null) data.reminders = new ArrayList<>();
		data.waypoints = new ArrayList<>(data.waypoints.stream().limit(MAX_WAYPOINTS).toList());
		data.reminders = new ArrayList<>(data.reminders.stream().limit(MAX_REMINDERS).toList());
		long next = data.reminders.stream().mapToLong(AssistantReminder::id).max().orElse(0) + 1;
		data.nextReminderId = Math.max(Math.max(1, data.nextReminderId), next);
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData root = new FileData();
		root.players = players;
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
			AiCompanionMod.LOGGER.error("Cannot save assistant-orb data to {}", path, error);
		}
	}
}
