package com.example.ai_companion.client.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Pure, deterministic board logic for the Minecraft-themed Tetris minigame. */
public final class TetrisGame {
	public static final int WIDTH = 10;
	public static final int HEIGHT = 20;

	public enum State { RUNNING, PAUSED, GAME_OVER }

	public enum Tetromino {
		I(0xFF4DD0E1, shape(
			cells(0, 1, 1, 1, 2, 1, 3, 1), cells(2, 0, 2, 1, 2, 2, 2, 3),
			cells(0, 2, 1, 2, 2, 2, 3, 2), cells(1, 0, 1, 1, 1, 2, 1, 3))),
		J(0xFF4267B2, shape(
			cells(0, 0, 0, 1, 1, 1, 2, 1), cells(1, 0, 2, 0, 1, 1, 1, 2),
			cells(0, 1, 1, 1, 2, 1, 2, 2), cells(1, 0, 1, 1, 0, 2, 1, 2))),
		L(0xFFFF9800, shape(
			cells(2, 0, 0, 1, 1, 1, 2, 1), cells(1, 0, 1, 1, 1, 2, 2, 2),
			cells(0, 1, 1, 1, 2, 1, 0, 2), cells(0, 0, 1, 0, 1, 1, 1, 2))),
		O(0xFFFFD54F, shape(
			cells(1, 0, 2, 0, 1, 1, 2, 1), cells(1, 0, 2, 0, 1, 1, 2, 1),
			cells(1, 0, 2, 0, 1, 1, 2, 1), cells(1, 0, 2, 0, 1, 1, 2, 1))),
		S(0xFF66BB6A, shape(
			cells(1, 0, 2, 0, 0, 1, 1, 1), cells(1, 0, 1, 1, 2, 1, 2, 2),
			cells(1, 1, 2, 1, 0, 2, 1, 2), cells(0, 0, 0, 1, 1, 1, 1, 2))),
		T(0xFFAB47BC, shape(
			cells(1, 0, 0, 1, 1, 1, 2, 1), cells(1, 0, 1, 1, 2, 1, 1, 2),
			cells(0, 1, 1, 1, 2, 1, 1, 2), cells(1, 0, 0, 1, 1, 1, 1, 2))),
		Z(0xFFEF5350, shape(
			cells(0, 0, 1, 0, 1, 1, 2, 1), cells(2, 0, 1, 1, 2, 1, 1, 2),
			cells(0, 1, 1, 1, 1, 2, 2, 2), cells(1, 0, 0, 1, 1, 1, 0, 2)));

		private final int color;
		private final int[][][] rotations;

		Tetromino(int color, int[][][] rotations) {
			this.color = color;
			this.rotations = rotations;
		}

		public int color() {
			return color;
		}

		int[][] cells(int rotation) {
			return rotations[Math.floorMod(rotation, rotations.length)];
		}

		private static int[][] cells(int... coordinates) {
			int[][] result = new int[coordinates.length / 2][2];
			for (int index = 0; index < coordinates.length; index += 2) {
				result[index / 2][0] = coordinates[index];
				result[index / 2][1] = coordinates[index + 1];
			}
			return result;
		}

		private static int[][][] shape(int[][]... rotations) {
			return rotations;
		}
	}

	public record Cell(int x, int y, Tetromino type, boolean ghost) { }

	public record StepResult(boolean locked, int linesCleared, boolean gameOver) {
		static StepResult moved() {
			return new StepResult(false, 0, false);
		}
	}

	private final Random random;
	private final int[][] board = new int[HEIGHT][WIDTH];
	private final List<Tetromino> bag = new ArrayList<>();
	private Tetromino current;
	private Tetromino next;
	private int rotation;
	private int pieceX;
	private int pieceY;
	private int score;
	private int lines;
	private int level;
	private State state;

	public TetrisGame() {
		this(new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public TetrisGame(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		reset();
	}

	public void reset() {
		for (int[] row : board) java.util.Arrays.fill(row, 0);
		bag.clear();
		score = 0;
		lines = 0;
		level = 1;
		state = State.RUNNING;
		next = takeFromBag();
		spawnPiece();
	}

	public boolean moveLeft() {
		return move(-1, 0);
	}

	public boolean moveRight() {
		return move(1, 0);
	}

	public StepResult softDrop() {
		if (state != State.RUNNING) return StepResult.moved();
		if (move(0, 1)) {
			score++;
			return StepResult.moved();
		}
		return lockPiece();
	}

	public StepResult tick() {
		if (state != State.RUNNING) return StepResult.moved();
		if (move(0, 1)) return StepResult.moved();
		return lockPiece();
	}

	public StepResult hardDrop() {
		if (state != State.RUNNING) return StepResult.moved();
		int distance = 0;
		while (move(0, 1)) distance++;
		score += distance * 2;
		return lockPiece();
	}

	public boolean rotateClockwise() {
		if (state != State.RUNNING) return false;
		int nextRotation = (rotation + 1) % 4;
		for (int kick : new int[]{0, -1, 1, -2, 2}) {
			if (!collides(pieceX + kick, pieceY, nextRotation)) {
				pieceX += kick;
				rotation = nextRotation;
				return true;
			}
		}
		return false;
	}

	public void togglePause() {
		if (state == State.RUNNING) state = State.PAUSED;
		else if (state == State.PAUSED) state = State.RUNNING;
	}

	private boolean move(int dx, int dy) {
		if (state != State.RUNNING || collides(pieceX + dx, pieceY + dy, rotation)) return false;
		pieceX += dx;
		pieceY += dy;
		return true;
	}

	private StepResult lockPiece() {
		for (int[] cell : current.cells(rotation)) {
			int x = pieceX + cell[0];
			int y = pieceY + cell[1];
			if (y >= 0 && y < HEIGHT && x >= 0 && x < WIDTH) board[y][x] = current.ordinal() + 1;
		}
		int cleared = clearLines();
		if (cleared > 0) {
			int lineScore = switch (cleared) {
				case 1 -> 100;
				case 2 -> 300;
				case 3 -> 500;
				default -> 800;
			};
			score += lineScore * level;
			lines += cleared;
			level = lines / 10 + 1;
		}
		spawnPiece();
		return new StepResult(true, cleared, state == State.GAME_OVER);
	}

	private int clearLines() {
		int cleared = 0;
		for (int y = HEIGHT - 1; y >= 0; y--) {
			boolean full = true;
			for (int x = 0; x < WIDTH; x++) {
				if (board[y][x] == 0) {
					full = false;
					break;
				}
			}
			if (!full) continue;
			cleared++;
			for (int moveY = y; moveY > 0; moveY--) {
				System.arraycopy(board[moveY - 1], 0, board[moveY], 0, WIDTH);
			}
			java.util.Arrays.fill(board[0], 0);
			y++;
		}
		return cleared;
	}

	private void spawnPiece() {
		current = next;
		next = takeFromBag();
		rotation = 0;
		pieceX = 3;
		pieceY = 0;
		if (collides(pieceX, pieceY, rotation)) state = State.GAME_OVER;
	}

	private Tetromino takeFromBag() {
		if (bag.isEmpty()) {
			Collections.addAll(bag, Tetromino.values());
			Collections.shuffle(bag, random);
		}
		return bag.removeLast();
	}

	private boolean collides(int x, int y, int candidateRotation) {
		for (int[] cell : current.cells(candidateRotation)) {
			int boardX = x + cell[0];
			int boardY = y + cell[1];
			if (boardX < 0 || boardX >= WIDTH || boardY >= HEIGHT) return true;
			if (boardY >= 0 && board[boardY][boardX] != 0) return true;
		}
		return false;
	}

	public int cellColor(int x, int y) {
		if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return 0;
		int stored = board[y][x];
		if (stored != 0) return Tetromino.values()[stored - 1].color();
		for (int[] cell : current.cells(rotation)) {
			if (pieceX + cell[0] == x && pieceY + cell[1] == y) return current.color();
		}
		return 0;
	}

	public List<Cell> ghostCells() {
		int ghostY = pieceY;
		while (!collides(pieceX, ghostY + 1, rotation)) ghostY++;
		List<Cell> result = new ArrayList<>(4);
		for (int[] cell : current.cells(rotation)) {
			result.add(new Cell(pieceX + cell[0], ghostY + cell[1], current, true));
		}
		return List.copyOf(result);
	}

	public List<Cell> nextCells() {
		List<Cell> result = new ArrayList<>(4);
		for (int[] cell : next.cells(0)) result.add(new Cell(cell[0], cell[1], next, false));
		return List.copyOf(result);
	}

	public int automaticDropIntervalTicks() {
		return Math.max(2, 12 - level);
	}

	public int score() {
		return score;
	}

	public int lines() {
		return lines;
	}

	public int level() {
		return level;
	}

	public State state() {
		return state;
	}

	public Tetromino current() {
		return current;
	}

	public Tetromino next() {
		return next;
	}
}
