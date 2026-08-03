package com.example.ai_companion.client.minigame;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-local high scores and unlock progress for the minigame center. */
public final class MinigameScores {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-minigame-scores.json");

	public int snakeHighScore;
	public int tetrisHighScore;
	public int tetrisBestLines;
	public boolean snakeMasterTitle;

	public static MinigameScores load() {
		try {
			if (Files.notExists(PATH)) return new MinigameScores();
			MinigameScores scores = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				MinigameScores.class);
			return scores == null ? new MinigameScores() : scores;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read minigame scores {}; using defaults", PATH, error);
			return new MinigameScores();
		}
	}

	public boolean recordSnake(int score) {
		boolean changed = false;
		if (score > snakeHighScore) {
			snakeHighScore = score;
			changed = true;
		}
		if (score >= 500 && !snakeMasterTitle) {
			snakeMasterTitle = true;
			changed = true;
		}
		if (changed) saveQuietly();
		return changed;
	}

	public boolean recordTetris(int score, int lines) {
		boolean changed = false;
		if (score > tetrisHighScore) {
			tetrisHighScore = score;
			changed = true;
		}
		if (lines > tetrisBestLines) {
			tetrisBestLines = lines;
			changed = true;
		}
		if (changed) saveQuietly();
		return changed;
	}

	private void saveQuietly() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save minigame scores {}", PATH, error);
		}
	}
}
