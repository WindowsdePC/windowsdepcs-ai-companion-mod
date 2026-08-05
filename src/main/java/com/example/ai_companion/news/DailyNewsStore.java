package com.example.ai_companion.news;

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
import java.util.List;

/** Bounded global event stream and Minecraft Daily archive. */
public final class DailyNewsStore implements AutoCloseable {
	public static final int MAX_EVENTS = 2_048;
	public static final int MAX_ISSUES = 64;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class FileData {
		List<NewsEvent> events = new ArrayList<>();
		List<DailyNewsIssue> issues = new ArrayList<>();
		long nextEventId = 1;
		long nextIssueId = 1;
	}

	private final Path path;
	private final FileData data;

	public static DailyNewsStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-daily-news.json");
		try {
			return load(path);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty daily news", path, error);
			return new DailyNewsStore(path, new FileData());
		}
	}

	static DailyNewsStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new DailyNewsStore(path, new FileData());
		FileData loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		return new DailyNewsStore(path, loaded == null ? new FileData() : loaded);
	}

	private DailyNewsStore(Path path, FileData data) {
		this.path = path;
		this.data = data;
		normalize(data);
	}

	public synchronized NewsEvent record(long minecraftDay, NewsCategory category, String message)
			throws IOException {
		NewsEvent event = new NewsEvent(data.nextEventId++, minecraftDay, System.currentTimeMillis(),
			category, message);
		data.events.add(event);
		trimOldest(data.events, MAX_EVENTS);
		save();
		return event;
	}

	public synchronized List<NewsEvent> eventsForDay(long minecraftDay) {
		return data.events.stream().filter(event -> event.minecraftDay() == minecraftDay)
			.sorted(Comparator.comparingLong(NewsEvent::id)).toList();
	}

	public synchronized DailyNewsIssue upsertIssue(long minecraftDay, String title, String body)
			throws IOException {
		for (int index = 0; index < data.issues.size(); index++) {
			DailyNewsIssue issue = data.issues.get(index);
			if (issue.minecraftDay() != minecraftDay) continue;
			DailyNewsIssue updated = new DailyNewsIssue(issue.id(), minecraftDay,
				System.currentTimeMillis(), title, body, "");
			data.issues.set(index, updated);
			save();
			return updated;
		}
		DailyNewsIssue issue = new DailyNewsIssue(data.nextIssueId++, minecraftDay,
			System.currentTimeMillis(), title, body, "");
		data.issues.add(issue);
		trimOldest(data.issues, MAX_ISSUES);
		save();
		return issue;
	}

	public synchronized DailyNewsIssue saveAiEdition(long issueId, String aiEdition) throws IOException {
		for (int index = 0; index < data.issues.size(); index++) {
			DailyNewsIssue issue = data.issues.get(index);
			if (issue.id() != issueId) continue;
			DailyNewsIssue updated = issue.withAiEdition(aiEdition);
			data.issues.set(index, updated);
			save();
			return updated;
		}
		throw new IllegalArgumentException("找不到日报 #" + issueId);
	}

	public synchronized List<DailyNewsIssue> issues() {
		return data.issues.stream().sorted(Comparator.comparingLong(DailyNewsIssue::id).reversed()).toList();
	}

	public synchronized DailyNewsIssue requireIssue(long id) {
		return data.issues.stream().filter(issue -> issue.id() == id).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("找不到日报 #" + id));
	}

	private static void normalize(FileData data) {
		if (data.events == null) data.events = new ArrayList<>();
		if (data.issues == null) data.issues = new ArrayList<>();
		data.events = new ArrayList<>(data.events.stream().filter(java.util.Objects::nonNull).toList());
		data.issues = new ArrayList<>(data.issues.stream().filter(java.util.Objects::nonNull).toList());
		trimOldest(data.events, MAX_EVENTS);
		trimOldest(data.issues, MAX_ISSUES);
		long nextEvent = data.events.stream().mapToLong(NewsEvent::id).max().orElse(0) + 1;
		long nextIssue = data.issues.stream().mapToLong(DailyNewsIssue::id).max().orElse(0) + 1;
		data.nextEventId = Math.max(Math.max(1, data.nextEventId), nextEvent);
		data.nextIssueId = Math.max(Math.max(1, data.nextIssueId), nextIssue);
	}

	private static <T> void trimOldest(List<T> values, int limit) {
		if (values.size() > limit) values.subList(0, values.size() - limit).clear();
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
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
			AiCompanionMod.LOGGER.error("Cannot save Minecraft Daily data to {}", path, error);
		}
	}
}
