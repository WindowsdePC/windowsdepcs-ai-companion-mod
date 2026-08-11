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
		boolean maximumWorldBorderEnabled, int schemaVersion) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-world-features.json");

	public static WorldFeatureConfig defaults() {
		return new WorldFeatureConfig(true, false, false, 1);
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
			if (loaded == null) return defaults();
			if (loaded.schemaVersion() < 1) {
				WorldFeatureConfig migrated = new WorldFeatureConfig(true, loaded.mercifulVoidEnabled(),
					loaded.maximumWorldBorderEnabled(), 1);
				migrated.save();
				return migrated;
			}
			return loaded;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; using world feature defaults", PATH, error);
			return defaults();
		}
	}

	public WorldFeatureConfig withNavigatorEnabled(boolean value) {
		return new WorldFeatureConfig(value, mercifulVoidEnabled, maximumWorldBorderEnabled, 1);
	}

	public WorldFeatureConfig withMercifulVoidEnabled(boolean value) {
		return new WorldFeatureConfig(navigatorEnabled, value, maximumWorldBorderEnabled, 1);
	}

	public WorldFeatureConfig withMaximumWorldBorderEnabled(boolean value) {
		return new WorldFeatureConfig(navigatorEnabled, mercifulVoidEnabled, value, 1);
	}

	public void save() throws IOException {
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
