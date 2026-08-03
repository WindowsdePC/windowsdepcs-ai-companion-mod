package com.example.ai_companion.client.minigame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;

/** Pure game-state implementation for the Minecraft-block Minesweeper minigame. */
public final class MinesweeperGame {
	public enum Difficulty {
		BEGINNER("初级", 9, 9, 10),
		INTERMEDIATE("中级", 16, 16, 40),
		EXPERT("专家", 30, 16, 99);

		private final String displayName;
		private final int width;
		private final int height;
		private final int mines;

		Difficulty(String displayName, int width, int height, int mines) {
			this.displayName = displayName;
			this.width = width;
			this.height = height;
			this.mines = mines;
		}

		public String displayName() { return displayName; }
		public int width() { return width; }
		public int height() { return height; }
		public int mines() { return mines; }
	}

	public enum State { READY, RUNNING, WON, LOST }

	public enum ActionResult { NO_CHANGE, REVEALED, FLAGGED, EXPLODED, WON }

	public record Cell(int x, int y, boolean revealed, boolean flagged, boolean mine,
			int adjacentMines) { }

	private final Random random;
	private Difficulty difficulty;
	private boolean[][] mines;
	private boolean[][] revealed;
	private boolean[][] flagged;
	private State state;
	private int revealedCount;
	private int flaggedCount;
	private int elapsedTicks;

	public MinesweeperGame() {
		this(Difficulty.BEGINNER, new Random());
	}

	public MinesweeperGame(Difficulty difficulty) {
		this(difficulty, new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public MinesweeperGame(Difficulty difficulty, Random random) {
		this.random = Objects.requireNonNull(random, "random");
		reset(difficulty);
	}

	public void reset() {
		reset(difficulty);
	}

	public void reset(Difficulty nextDifficulty) {
		difficulty = Objects.requireNonNull(nextDifficulty, "difficulty");
		mines = new boolean[height()][width()];
		revealed = new boolean[height()][width()];
		flagged = new boolean[height()][width()];
		state = State.READY;
		revealedCount = 0;
		flaggedCount = 0;
		elapsedTicks = 0;
	}

	public void tick() {
		if (state == State.RUNNING) elapsedTicks++;
	}

	public ActionResult reveal(int x, int y) {
		if (!inside(x, y) || state == State.WON || state == State.LOST || flagged[y][x]) {
			return ActionResult.NO_CHANGE;
		}
		if (state == State.READY) {
			placeMines(x, y);
			state = State.RUNNING;
		}
		if (revealed[y][x]) return chord(x, y);
		if (mines[y][x]) {
			revealed[y][x] = true;
			state = State.LOST;
			return ActionResult.EXPLODED;
		}
		revealSafeArea(x, y);
		return checkWin();
	}

	public ActionResult toggleFlag(int x, int y) {
		if (!inside(x, y) || revealed[y][x] || state == State.WON || state == State.LOST) {
			return ActionResult.NO_CHANGE;
		}
		flagged[y][x] = !flagged[y][x];
		flaggedCount += flagged[y][x] ? 1 : -1;
		return ActionResult.FLAGGED;
	}

	private ActionResult chord(int x, int y) {
		int adjacent = adjacentMineCount(x, y);
		if (adjacent == 0 || adjacentFlagCount(x, y) != adjacent) return ActionResult.NO_CHANGE;
		for (int ny = y - 1; ny <= y + 1; ny++) {
			for (int nx = x - 1; nx <= x + 1; nx++) {
				if (!inside(nx, ny) || revealed[ny][nx] || flagged[ny][nx]) continue;
				if (mines[ny][nx]) {
					revealed[ny][nx] = true;
					state = State.LOST;
					return ActionResult.EXPLODED;
				}
				revealSafeArea(nx, ny);
			}
		}
		return checkWin();
	}

	private void placeMines(int safeX, int safeY) {
		List<Integer> candidates = new ArrayList<>(width() * height());
		for (int y = 0; y < height(); y++) {
			for (int x = 0; x < width(); x++) {
				if (Math.abs(x - safeX) <= 1 && Math.abs(y - safeY) <= 1) continue;
				candidates.add(y * width() + x);
			}
		}
		Collections.shuffle(candidates, random);
		for (int index = 0; index < mineCount(); index++) {
			int packed = candidates.get(index);
			mines[packed / width()][packed % width()] = true;
		}
	}

	private void revealSafeArea(int startX, int startY) {
		Queue<Integer> queue = new ArrayDeque<>();
		queue.add(startY * width() + startX);
		while (!queue.isEmpty()) {
			int packed = queue.remove();
			int x = packed % width();
			int y = packed / width();
			if (!inside(x, y) || revealed[y][x] || flagged[y][x] || mines[y][x]) continue;
			revealed[y][x] = true;
			revealedCount++;
			if (adjacentMineCount(x, y) != 0) continue;
			for (int ny = y - 1; ny <= y + 1; ny++) {
				for (int nx = x - 1; nx <= x + 1; nx++) {
					if (inside(nx, ny) && !revealed[ny][nx]) queue.add(ny * width() + nx);
				}
			}
		}
	}

	private ActionResult checkWin() {
		if (revealedCount == width() * height() - mineCount()) {
			state = State.WON;
			return ActionResult.WON;
		}
		return ActionResult.REVEALED;
	}

	private int adjacentFlagCount(int x, int y) {
		int count = 0;
		for (int ny = y - 1; ny <= y + 1; ny++) {
			for (int nx = x - 1; nx <= x + 1; nx++) {
				if (inside(nx, ny) && flagged[ny][nx]) count++;
			}
		}
		return count;
	}

	private int adjacentMineCount(int x, int y) {
		int count = 0;
		for (int ny = y - 1; ny <= y + 1; ny++) {
			for (int nx = x - 1; nx <= x + 1; nx++) {
				if (inside(nx, ny) && mines[ny][nx]) count++;
			}
		}
		return count;
	}

	private boolean inside(int x, int y) {
		return x >= 0 && x < width() && y >= 0 && y < height();
	}

	public Cell cell(int x, int y) {
		if (!inside(x, y)) throw new IndexOutOfBoundsException(x + "," + y);
		return new Cell(x, y, revealed[y][x], flagged[y][x], mines[y][x],
			adjacentMineCount(x, y));
	}

	public Difficulty difficulty() { return difficulty; }
	public int width() { return difficulty.width(); }
	public int height() { return difficulty.height(); }
	public int mineCount() { return difficulty.mines(); }
	public State state() { return state; }
	public int revealedCount() { return revealedCount; }
	public int flaggedCount() { return flaggedCount; }
	public int remainingMineEstimate() { return mineCount() - flaggedCount; }
	public int elapsedTicks() { return elapsedTicks; }
}
