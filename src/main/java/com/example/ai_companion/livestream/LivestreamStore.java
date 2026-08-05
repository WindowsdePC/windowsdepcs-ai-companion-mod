package com.example.ai_companion.livestream;

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
import java.util.Map;
import java.util.UUID;

/** JSON persistence for player-owned AI livestream sessions. */
public final class LivestreamStore implements AutoCloseable {
	public static final int MAX_SESSIONS = 64;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class FileData {
		List<LivestreamSession> sessions = new ArrayList<>();
	}

	private final Path path;
	private final Map<UUID, LivestreamSession> sessions;

	public static LivestreamStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-livestreams.json");
		try {
			return load(path);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with no livestreams", path, error);
			return new LivestreamStore(path, new LinkedHashMap<>());
		}
	}

	static LivestreamStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new LivestreamStore(path, new LinkedHashMap<>());
		FileData loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<UUID, LivestreamSession> normalized = new LinkedHashMap<>();
		if (loaded != null && loaded.sessions != null) {
			for (LivestreamSession session : loaded.sessions) {
				if (session == null || normalized.size() >= MAX_SESSIONS) continue;
				try {
					LivestreamSession safe = new LivestreamSession(session.playerId(), session.playerName(),
						session.viewers(), session.intervalTicks(), session.commentsGenerated(), session.enabled());
					normalized.put(safe.playerId(), safe);
				} catch (RuntimeException ignored) { }
			}
		}
		return new LivestreamStore(path, normalized);
	}

	private LivestreamStore(Path path, Map<UUID, LivestreamSession> sessions) {
		this.path = path;
		this.sessions = sessions;
	}

	public synchronized LivestreamSession put(LivestreamSession session) throws IOException {
		if (!sessions.containsKey(session.playerId()) && sessions.size() >= MAX_SESSIONS) {
			throw new IllegalStateException("直播会话已达服务器上限 " + MAX_SESSIONS);
		}
		sessions.put(session.playerId(), session);
		save();
		return session;
	}

	public synchronized LivestreamSession require(UUID playerId) {
		LivestreamSession session = sessions.get(playerId);
		if (session == null) throw new IllegalArgumentException("你还没有 AI 直播会话");
		return session;
	}

	public synchronized List<LivestreamSession> sessions() {
		return List.copyOf(sessions.values());
	}

	public synchronized boolean remove(UUID playerId) throws IOException {
		boolean removed = sessions.remove(playerId) != null;
		if (removed) save();
		return removed;
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData data = new FileData();
		data.sessions = new ArrayList<>(sessions.values());
		Files.writeString(temporary, GSON.toJson(data) + System.lineSeparator(), StandardCharsets.UTF_8);
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
			AiCompanionMod.LOGGER.error("Cannot save livestream sessions to {}", path, error);
		}
	}
}
