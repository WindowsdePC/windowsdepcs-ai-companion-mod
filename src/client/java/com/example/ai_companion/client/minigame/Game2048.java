package com.example.ai_companion.client.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Pure game-state implementation for the classic 4x4 2048 minigame. */
public final class Game2048 {
	public static final int SIZE = 4;

	public enum Direction { UP, DOWN, LEFT, RIGHT }

	public enum State { RUNNING, PAUSED, WON, GAME_OVER }

	public record MoveResult(boolean changed, int scoreGained, int largestMerge,
			boolean reached2048, boolean gameOver) {
		static MoveResult unchanged(boolean gameOver) {
			return new MoveResult(false, 0, 0, false, gameOver);
		}
	}

	private final Random random;
	private final int[][] board = new int[SIZE][SIZE];
	private int[][] undoBoard;
	private int undoScore;
	private State undoState;
	private boolean undoReached2048;
	private boolean undoAvailable;
	private State state;
	private int score;
	private boolean reached2048;

	public Game2048() {
		this(new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public Game2048(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		reset();
	}

	public void reset() {
		for (int[] row : board) java.util.Arrays.fill(row, 0);
		score = 0;
		reached2048 = false;
		undoBoard = null;
		undoAvailable = false;
		state = State.RUNNING;
		spawnTile();
		spawnTile();
	}

	public MoveResult move(Direction direction) {
		Objects.requireNonNull(direction, "direction");
		if (state != State.RUNNING) return MoveResult.unchanged(state == State.GAME_OVER);
		int[][] boardBeforeMove = copyBoard();
		int beforeScore = score;
		State beforeState = state;
		boolean beforeReached2048 = reached2048;
		boolean changed = false;
		int gained = 0;
		int largestMerge = 0;
		for (int line = 0; line < SIZE; line++) {
			int[] before = readLine(direction, line);
			Merge merged = mergeLine(before);
			if (!java.util.Arrays.equals(before, merged.values())) {
				changed = true;
				writeLine(direction, line, merged.values());
			}
			gained += merged.score();
			largestMerge = Math.max(largestMerge, merged.largest());
		}
		if (!changed) {
			boolean gameOver = !hasAvailableMove();
			if (gameOver) state = State.GAME_OVER;
			return MoveResult.unchanged(gameOver);
		}
		undoBoard = boardBeforeMove;
		undoScore = beforeScore;
		undoState = beforeState;
		undoReached2048 = beforeReached2048;
		undoAvailable = true;
		score += gained;
		spawnTile();
		boolean newlyReached = !reached2048 && bestTile() >= 2048;
		if (newlyReached) {
			reached2048 = true;
			state = State.WON;
		} else if (!hasAvailableMove()) {
			state = State.GAME_OVER;
		}
		return new MoveResult(true, gained, largestMerge, newlyReached, state == State.GAME_OVER);
	}

	/** Restores the board and score from the last effective move. */
	public boolean undo() {
		if (!undoAvailable || undoBoard == null) return false;
		for (int y = 0; y < SIZE; y++) System.arraycopy(undoBoard[y], 0, board[y], 0, SIZE);
		score = undoScore;
		state = undoState;
		reached2048 = undoReached2048;
		undoBoard = null;
		undoAvailable = false;
		return true;
	}

	public void togglePause() {
		if (state == State.RUNNING) state = State.PAUSED;
		else if (state == State.PAUSED) state = State.RUNNING;
	}

	public void continueAfterWin() {
		if (state == State.WON) {
			state = hasAvailableMove() ? State.RUNNING : State.GAME_OVER;
		}
	}

	private int[] readLine(Direction direction, int line) {
		int[] values = new int[SIZE];
		for (int index = 0; index < SIZE; index++) {
			int x = switch (direction) {
				case LEFT -> index;
				case RIGHT -> SIZE - 1 - index;
				case UP, DOWN -> line;
			};
			int y = switch (direction) {
				case UP -> index;
				case DOWN -> SIZE - 1 - index;
				case LEFT, RIGHT -> line;
			};
			values[index] = board[y][x];
		}
		return values;
	}

	private void writeLine(Direction direction, int line, int[] values) {
		for (int index = 0; index < SIZE; index++) {
			int x = switch (direction) {
				case LEFT -> index;
				case RIGHT -> SIZE - 1 - index;
				case UP, DOWN -> line;
			};
			int y = switch (direction) {
				case UP -> index;
				case DOWN -> SIZE - 1 - index;
				case LEFT, RIGHT -> line;
			};
			board[y][x] = values[index];
		}
	}

	private static Merge mergeLine(int[] source) {
		int[] compact = new int[SIZE];
		int used = 0;
		for (int value : source) if (value != 0) compact[used++] = value;
		int[] result = new int[SIZE];
		int output = 0;
		int score = 0;
		int largest = 0;
		for (int index = 0; index < used; index++) {
			int value = compact[index];
			if (index + 1 < used && compact[index + 1] == value) {
				value *= 2;
				index++;
				score += value;
				largest = Math.max(largest, value);
			}
			result[output++] = value;
		}
		return new Merge(result, score, largest);
	}

	private void spawnTile() {
		List<Integer> empty = new ArrayList<>();
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (board[y][x] == 0) empty.add(y * SIZE + x);
			}
		}
		if (empty.isEmpty()) return;
		int packed = empty.get(random.nextInt(empty.size()));
		board[packed / SIZE][packed % SIZE] = random.nextInt(10) == 0 ? 4 : 2;
	}

	private boolean hasAvailableMove() {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (board[y][x] == 0) return true;
				if (x + 1 < SIZE && board[y][x] == board[y][x + 1]) return true;
				if (y + 1 < SIZE && board[y][x] == board[y + 1][x]) return true;
			}
		}
		return false;
	}

	public int valueAt(int x, int y) {
		if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) throw new IndexOutOfBoundsException(x + "," + y);
		return board[y][x];
	}

	public int bestTile() {
		int best = 0;
		for (int[] row : board) for (int value : row) best = Math.max(best, value);
		return best;
	}

	public int score() {
		return score;
	}

	public State state() {
		return state;
	}

	public boolean reached2048() {
		return reached2048;
	}

	public boolean canUndo() {
		return undoAvailable;
	}

	void setBoardForTesting(int[][] values) {
		if (values.length != SIZE) throw new IllegalArgumentException("board height");
		for (int y = 0; y < SIZE; y++) {
			if (values[y].length != SIZE) throw new IllegalArgumentException("board width");
			System.arraycopy(values[y], 0, board[y], 0, SIZE);
		}
		state = State.RUNNING;
		reached2048 = bestTile() >= 2048;
		undoBoard = null;
		undoAvailable = false;
	}

	private int[][] copyBoard() {
		int[][] copy = new int[SIZE][SIZE];
		for (int y = 0; y < SIZE; y++) System.arraycopy(board[y], 0, copy[y], 0, SIZE);
		return copy;
	}

	private record Merge(int[] values, int score, int largest) { }
}
