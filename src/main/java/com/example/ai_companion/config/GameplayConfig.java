package com.example.ai_companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent server-side configuration for optional gameplay features. */
public record GameplayConfig(boolean goldenSpearRushEnabled, int durabilityEvery,
							 int hungerEvery, int hungerCost, double rushStrength,
							 boolean flexibleEquipmentEnabled) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-gameplay.json");

	public static GameplayConfig defaults() {
		// Vanilla Lunge II uses 0.458 * 2 = 0.916 horizontal impulse.
		return new GameplayConfig(true, 15, 30, 2, 0.916, false);
	}

	public static GameplayConfig load() {
		try {
			if (Files.notExists(PATH)) {
				GameplayConfig created = defaults();
				created.save();
				return created;
			}
			GameplayConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				GameplayConfig.class);
			return loaded == null ? defaults() : loaded.normalized();
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; using gameplay defaults", PATH, error);
			return defaults();
		}
	}

	public GameplayConfig normalized() {
		return new GameplayConfig(goldenSpearRushEnabled,
			Math.clamp(durabilityEvery, 1, 1000),
			Math.clamp(hungerEvery, 1, 1000),
			Math.clamp(hungerCost, 0, 20),
			Math.clamp(rushStrength, 0.1, 4.0), flexibleEquipmentEnabled);
	}

	public GameplayConfig withEnabled(boolean value) {
		return new GameplayConfig(value, durabilityEvery, hungerEvery, hungerCost, rushStrength,
			flexibleEquipmentEnabled).normalized();
	}

	public GameplayConfig withDurabilityEvery(int value) {
		return new GameplayConfig(goldenSpearRushEnabled, value, hungerEvery, hungerCost, rushStrength,
			flexibleEquipmentEnabled).normalized();
	}

	public GameplayConfig withHungerEvery(int value) {
		return new GameplayConfig(goldenSpearRushEnabled, durabilityEvery, value, hungerCost, rushStrength,
			flexibleEquipmentEnabled).normalized();
	}

	public GameplayConfig withHungerCost(int value) {
		return new GameplayConfig(goldenSpearRushEnabled, durabilityEvery, hungerEvery, value, rushStrength,
			flexibleEquipmentEnabled).normalized();
	}

	public GameplayConfig withRushStrength(double value) {
		return new GameplayConfig(goldenSpearRushEnabled, durabilityEvery, hungerEvery, hungerCost, value,
			flexibleEquipmentEnabled).normalized();
	}

	public GameplayConfig withFlexibleEquipmentEnabled(boolean value) {
		return new GameplayConfig(goldenSpearRushEnabled, durabilityEvery, hungerEvery, hungerCost,
			rushStrength, value).normalized();
	}

	public void save() throws IOException {
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(normalized()) + System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
