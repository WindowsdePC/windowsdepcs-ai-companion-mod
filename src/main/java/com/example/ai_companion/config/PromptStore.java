package com.example.ai_companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.ai.PromptTemplates;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent named prompt presets shared by commands and the client editor. */
public final class PromptStore {
	public static final int MAX_ID_LENGTH = 32;
	// Sent through the bounded direct UI payload; commands remain an optional compatibility path.
	public static final int MAX_PROMPT_LENGTH = 6_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Map<String, String> prompts;

	private PromptStore(Path path, Map<String, String> prompts) {
		this.path = path;
		this.prompts = new LinkedHashMap<>(prompts);
		PromptTemplates.defaults().forEach(this.prompts::putIfAbsent);
	}

	public static PromptStore loadServer() {
		return load(FabricLoader.getInstance().getConfigDir().resolve("windowsdepcs-ai-companion-prompts.json"));
	}

	public static PromptStore loadClient() {
		return load(FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-client-prompts.json"));
	}

	private static PromptStore load(Path path) {
		try {
			if (Files.notExists(path)) {
				PromptStore created = new PromptStore(path, PromptTemplates.defaults());
				created.save();
				return created;
			}
			Data data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
			return new PromptStore(path, data == null || data.prompts == null
				? PromptTemplates.defaults() : data.prompts);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read prompt store {}; using defaults", path, error);
			return new PromptStore(path, PromptTemplates.defaults());
		}
	}

	public synchronized Collection<String> ids() {
		return prompts.keySet().stream().toList();
	}

	public synchronized boolean contains(String id) {
		return prompts.containsKey(normalizeId(id));
	}

	public synchronized String get(String id) {
		String value = prompts.get(normalizeId(id));
		if (value == null) throw new IllegalArgumentException("找不到提示词预设: " + id);
		return value;
	}

	public synchronized void put(String id, String prompt) throws IOException {
		String normalized = validateId(id);
		if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("提示词不能为空");
		if (prompt.length() > MAX_PROMPT_LENGTH) {
			throw new IllegalArgumentException("提示词最多 " + MAX_PROMPT_LENGTH + " 个字符");
		}
		prompts.put(normalized, prompt.strip());
		save();
	}

	public synchronized void remove(String id) throws IOException {
		String normalized = validateId(id);
		if (PromptTemplates.defaults().containsKey(normalized)) {
			throw new IllegalArgumentException("内置预设不能删除，可使用 reset 恢复");
		}
		if (prompts.remove(normalized) == null) throw new IllegalArgumentException("找不到提示词预设: " + id);
		save();
	}

	public synchronized void reset(String id) throws IOException {
		String normalized = validateId(id);
		String original = PromptTemplates.defaults().get(normalized);
		if (original == null) throw new IllegalArgumentException("只有内置预设可以恢复: " + id);
		prompts.put(normalized, original);
		save();
	}

	public synchronized void save() throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(new Data(prompts)) + System.lineSeparator(),
			StandardCharsets.UTF_8);
	}

	public static String validateId(String id) {
		String normalized = normalizeId(id);
		if (normalized.isBlank() || normalized.length() > MAX_ID_LENGTH
			|| !normalized.matches("[a-z0-9_.-]+")) {
			throw new IllegalArgumentException("预设 ID 只能包含小写字母、数字、点、横线和下划线，最长 "
				+ MAX_ID_LENGTH + " 位");
		}
		return normalized;
	}

	private static String normalizeId(String id) {
		return id == null ? "" : id.strip().toLowerCase();
	}

	private static final class Data {
		final Map<String, String> prompts;

		Data(Map<String, String> prompts) {
			this.prompts = new LinkedHashMap<>(prompts);
		}
	}
}
