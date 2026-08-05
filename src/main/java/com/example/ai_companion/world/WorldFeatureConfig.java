package com.example.ai_companion.world;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent, server-authoritative switches for experimental world features. */
public record WorldFeatureConfig(boolean navigatorEnabled, boolean mercifulVoidEnabled,
		boolean maximumWorldBorderEnabled) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-world-features.json");

	public static WorldFeatureConfig defaults() {
		return new WorldFeatureConfig(false, false, false);
	}

	public static WorldFeatureConfig load() {
		try {
			if (Files.notExists(PATH)) {
				WorldFeatureConfig created = defaults();
				created.save();
				return created;
			}
			WorldFeatureConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				WorldFeatureConfig.class);
			return loaded == null ? defaults() : loaded;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; using world feature defaults", PATH, error);
			return defaults();
		}
	}

	public WorldFeatureConfig withNavigatorEnabled(boolean value) {
		return new WorldFeatureConfig(value, mercifulVoidEnabled, maximumWorldBorderEnabled);
	}

	public WorldFeatureConfig withMercifulVoidEnabled(boolean value) {
		return new WorldFeatureConfig(navigatorEnabled, value, maximumWorldBorderEnabled);
	}

	public WorldFeatureConfig withMaximumWorldBorderEnabled(boolean value) {
		return new WorldFeatureConfig(navigatorEnabled, mercifulVoidEnabled, value);
	}

	public void save() throws IOException {
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
