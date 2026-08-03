package com.example.ai_companion.client.minigame;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client-local records and cosmetic unlocks for the minigame center. */
public final class MinigameProgress {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-minigames.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-minigame-scores.json");

	public enum SnakeSkin {
		CLASSIC("经典草绿", 0xFF66BB6A, 0xFFA5D6A7),
		GOLDEN("金苹果", 0xFFFFB300, 0xFFFFE082),
		DIAMOND("钻石", 0xFF00ACC1, 0xFF80DEEA);

		private final String displayName;
		private final int bodyColor;
		private final int headColor;

		SnakeSkin(String displayName, int bodyColor, int headColor) {
			this.displayName = displayName;
			this.bodyColor = bodyColor;
			this.headColor = headColor;
		}

		public String displayName() {
			return displayName;
		}

		public int bodyColor() {
			return bodyColor;
		}

		public int headColor() {
			return headColor;
		}
	}

	public int snakeHighScore;
	public boolean goldenSnakeSkinUnlocked;
	public boolean diamondSnakeSkinUnlocked;
	public boolean snakeMasterTitleUnlocked;
	public SnakeSkin selectedSnakeSkin = SnakeSkin.CLASSIC;
	public int tetrisHighScore;
	public int tetrisBestLines;
	public int tetrisTotalLines;
	public int minesweeperBestTimeTicks;
	public int minesweeperWins;
	public int minesweeperBestStreak;
	public int minesweeperCurrentStreak;
	public int game2048HighScore;
	public int game2048BestTile;
	public int game2048Wins;
	public int rpsWins;
	public int rpsLosses;
	public int rpsDraws;
	public int rpsCurrentStreak;
	public int rpsBestStreak;

	public static MinigameProgress load() {
		try {
			if (Files.notExists(PATH)) {
				MinigameProgress created = importLegacyOrCreate();
				created.save();
				return created;
			}
			MinigameProgress loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				MinigameProgress.class);
			return loaded == null ? new MinigameProgress() : loaded.normalized();
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read minigame progress {}; using defaults", PATH, error);
			return new MinigameProgress();
		}
	}

	private static MinigameProgress importLegacyOrCreate() throws IOException {
		MinigameProgress created = new MinigameProgress();
		if (Files.notExists(LEGACY_PATH)) return created;
		LegacyScores legacy = GSON.fromJson(Files.readString(LEGACY_PATH, StandardCharsets.UTF_8),
			LegacyScores.class);
		if (legacy == null) return created;
		created.snakeHighScore = Math.max(0, legacy.snakeHighScore);
		created.snakeMasterTitleUnlocked = legacy.snakeMasterTitle;
		created.tetrisHighScore = Math.max(0, legacy.tetrisHighScore);
		created.tetrisBestLines = Math.max(0, legacy.tetrisBestLines);
		return created.normalized();
	}

	public MinigameProgress normalized() {
		snakeHighScore = Math.max(0, snakeHighScore);
		tetrisHighScore = Math.max(0, tetrisHighScore);
		tetrisBestLines = Math.max(0, tetrisBestLines);
		tetrisTotalLines = Math.max(0, tetrisTotalLines);
		minesweeperBestTimeTicks = Math.max(0, minesweeperBestTimeTicks);
		minesweeperWins = Math.max(0, minesweeperWins);
		minesweeperBestStreak = Math.max(0, minesweeperBestStreak);
		minesweeperCurrentStreak = Math.max(0, minesweeperCurrentStreak);
		game2048HighScore = Math.max(0, game2048HighScore);
		game2048BestTile = Math.max(0, game2048BestTile);
		game2048Wins = Math.max(0, game2048Wins);
		rpsWins = Math.max(0, rpsWins);
		rpsLosses = Math.max(0, rpsLosses);
		rpsDraws = Math.max(0, rpsDraws);
		rpsCurrentStreak = Math.max(0, rpsCurrentStreak);
		rpsBestStreak = Math.max(0, rpsBestStreak);
		if (snakeHighScore >= 10) goldenSnakeSkinUnlocked = true;
		if (snakeHighScore >= 25) diamondSnakeSkinUnlocked = true;
		if (snakeHighScore >= 30) snakeMasterTitleUnlocked = true;
		if (selectedSnakeSkin == null || !isUnlocked(selectedSnakeSkin)) {
			selectedSnakeSkin = SnakeSkin.CLASSIC;
		}
		return this;
	}

	public List<String> recordSnakeScore(int score) {
		List<String> unlocks = new ArrayList<>();
		if (score > snakeHighScore) snakeHighScore = score;
		if (score >= 10 && !goldenSnakeSkinUnlocked) {
			goldenSnakeSkinUnlocked = true;
			unlocks.add("解锁蛇皮肤：金苹果");
		}
		if (score >= 25 && !diamondSnakeSkinUnlocked) {
			diamondSnakeSkinUnlocked = true;
			unlocks.add("解锁蛇皮肤：钻石");
		}
		if (score >= 30 && !snakeMasterTitleUnlocked) {
			snakeMasterTitleUnlocked = true;
			unlocks.add("解锁称号：贪吃蛇大师");
		}
		saveSafely();
		return List.copyOf(unlocks);
	}

	public void recordTetrisResult(int score, int lines) {
		tetrisHighScore = Math.max(tetrisHighScore, Math.max(0, score));
		tetrisBestLines = Math.max(tetrisBestLines, Math.max(0, lines));
		tetrisTotalLines += Math.max(0, lines);
		saveSafely();
	}

	public void recordMinesweeperResult(boolean won, int elapsedTicks) {
		if (won) {
			int safeTicks = Math.max(1, elapsedTicks);
			if (minesweeperBestTimeTicks == 0 || safeTicks < minesweeperBestTimeTicks) {
				minesweeperBestTimeTicks = safeTicks;
			}
			minesweeperWins++;
			minesweeperCurrentStreak++;
			minesweeperBestStreak = Math.max(minesweeperBestStreak, minesweeperCurrentStreak);
		} else {
			minesweeperCurrentStreak = 0;
		}
		saveSafely();
	}

	public void record2048Result(int score, int bestTile, boolean reached2048) {
		game2048HighScore = Math.max(game2048HighScore, Math.max(0, score));
		game2048BestTile = Math.max(game2048BestTile, Math.max(0, bestTile));
		if (reached2048) game2048Wins++;
		saveSafely();
	}

	public void recordRpsRound(RockPaperScissorsGame.Outcome outcome) {
		switch (outcome) {
			case PLAYER_WIN -> {
				rpsWins++;
				rpsCurrentStreak++;
				rpsBestStreak = Math.max(rpsBestStreak, rpsCurrentStreak);
			}
			case AI_WIN -> {
				rpsLosses++;
				rpsCurrentStreak = 0;
			}
			case DRAW -> rpsDraws++;
		}
		saveSafely();
	}

	public String minesweeperBestTime() {
		if (minesweeperBestTimeTicks <= 0) return "--:--";
		int seconds = minesweeperBestTimeTicks / 20;
		return "%02d:%02d".formatted(seconds / 60, seconds % 60);
	}

	public SnakeSkin cycleSnakeSkin() {
		SnakeSkin[] values = SnakeSkin.values();
		int start = selectedSnakeSkin.ordinal();
		for (int offset = 1; offset <= values.length; offset++) {
			SnakeSkin candidate = values[(start + offset) % values.length];
			if (isUnlocked(candidate)) {
				selectedSnakeSkin = candidate;
				saveSafely();
				return candidate;
			}
		}
		return selectedSnakeSkin;
	}

	public boolean isUnlocked(SnakeSkin skin) {
		return switch (skin) {
			case CLASSIC -> true;
			case GOLDEN -> goldenSnakeSkinUnlocked;
			case DIAMOND -> diamondSnakeSkinUnlocked;
		};
	}

	public String snakeTitle() {
		return snakeMasterTitleUnlocked ? "贪吃蛇大师" : "尚未解锁";
	}

	public void save() throws IOException {
		normalized();
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private void saveSafely() {
		try {
			save();
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save minigame progress {}", PATH, error);
		}
	}

	private static final class LegacyScores {
		int snakeHighScore;
		int tetrisHighScore;
		int tetrisBestLines;
		boolean snakeMasterTitle;
	}
}
