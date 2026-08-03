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
	public static final int WIDTH = 14;
	public static final int HEIGHT = 10;
	public static final int MINE_COUNT = 24;

	public enum State { READY, RUNNING, WON, LOST }

	public enum ActionResult { NO_CHANGE, REVEALED, FLAGGED, EXPLODED, WON }

	public record Cell(int x, int y, boolean revealed, boolean flagged, boolean mine,
			int adjacentMines) { }

	private final Random random;
	private final boolean[][] mines = new boolean[HEIGHT][WIDTH];
	private final boolean[][] revealed = new boolean[HEIGHT][WIDTH];
	private final boolean[][] flagged = new boolean[HEIGHT][WIDTH];
	private State state;
	private int revealedCount;
	private int flaggedCount;
	private int elapsedTicks;

	public MinesweeperGame() {
		this(new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public MinesweeperGame(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		reset();
	}

	public void reset() {
		for (int y = 0; y < HEIGHT; y++) {
			java.util.Arrays.fill(mines[y], false);
			java.util.Arrays.fill(revealed[y], false);
			java.util.Arrays.fill(flagged[y], false);
		}
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
		List<Integer> candidates = new ArrayList<>(WIDTH * HEIGHT);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				if (Math.abs(x - safeX) <= 1 && Math.abs(y - safeY) <= 1) continue;
				candidates.add(y * WIDTH + x);
			}
		}
		Collections.shuffle(candidates, random);
		for (int index = 0; index < MINE_COUNT; index++) {
			int packed = candidates.get(index);
			mines[packed / WIDTH][packed % WIDTH] = true;
		}
	}

	private void revealSafeArea(int startX, int startY) {
		Queue<Integer> queue = new ArrayDeque<>();
		queue.add(startY * WIDTH + startX);
		while (!queue.isEmpty()) {
			int packed = queue.remove();
			int x = packed % WIDTH;
			int y = packed / WIDTH;
			if (!inside(x, y) || revealed[y][x] || flagged[y][x] || mines[y][x]) continue;
			revealed[y][x] = true;
			revealedCount++;
			if (adjacentMineCount(x, y) != 0) continue;
			for (int ny = y - 1; ny <= y + 1; ny++) {
				for (int nx = x - 1; nx <= x + 1; nx++) {
					if (inside(nx, ny) && !revealed[ny][nx]) queue.add(ny * WIDTH + nx);
				}
			}
		}
	}

	private ActionResult checkWin() {
		if (revealedCount == WIDTH * HEIGHT - MINE_COUNT) {
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

	private static boolean inside(int x, int y) {
		return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
	}

	public Cell cell(int x, int y) {
		if (!inside(x, y)) throw new IndexOutOfBoundsException(x + "," + y);
		return new Cell(x, y, revealed[y][x], flagged[y][x], mines[y][x],
			adjacentMineCount(x, y));
	}

	public State state() {
		return state;
	}

	public int revealedCount() {
		return revealedCount;
	}

	public int flaggedCount() {
		return flaggedCount;
	}

	public int remainingMineEstimate() {
		return MINE_COUNT - flaggedCount;
	}

	public int elapsedTicks() {
		return elapsedTicks;
	}
}
