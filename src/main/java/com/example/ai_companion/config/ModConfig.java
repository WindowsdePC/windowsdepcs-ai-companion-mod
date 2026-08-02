package com.example.ai_companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent server-side API configuration. */
public record ModConfig(String apiBase, String model, String apiKey,
						int requestTimeoutSeconds, int maxOutputTokens) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion.json");

	public static ModConfig defaults() {
		return new ModConfig("https://api.openai.com/v1", "gpt-5-mini", "", 45, 300);
	}

	public static ModConfig load() {
		try {
			if (Files.notExists(PATH)) {
				ModConfig config = defaults();
				config.save();
				return config;
			}
			ModConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), ModConfig.class);
			return loaded == null ? defaults() : loaded.normalized();
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; using defaults", PATH, error);
			return defaults();
		}
	}

	public ModConfig normalized() {
		String base = apiBase == null || apiBase.isBlank() ? defaults().apiBase : apiBase.trim();
		String chosenModel = model == null || model.isBlank() ? defaults().model : model.trim();
		return new ModConfig(base, chosenModel, apiKey == null ? "" : apiKey.trim(),
			Math.clamp(requestTimeoutSeconds, 5, 120), Math.clamp(maxOutputTokens, 64, 2048));
	}

	public String effectiveApiKey() {
		String environmentKey = System.getenv("MCAI_API_KEY");
		return environmentKey == null || environmentKey.isBlank() ? apiKey : environmentKey.trim();
	}

	public boolean hasApiKey() {
		return !effectiveApiKey().isBlank();
	}

	public ModConfig withApiBase(String value) {
		return new ModConfig(value, model, apiKey, requestTimeoutSeconds, maxOutputTokens).normalized();
	}

	public ModConfig withModel(String value) {
		return new ModConfig(apiBase, value, apiKey, requestTimeoutSeconds, maxOutputTokens).normalized();
	}

	public ModConfig withApiKey(String value) {
		return new ModConfig(apiBase, model, value, requestTimeoutSeconds, maxOutputTokens).normalized();
	}

	public void save() throws IOException {
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(normalized()) + System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
