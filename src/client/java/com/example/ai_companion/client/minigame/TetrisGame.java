package com.example.ai_companion.client.minigame;

import java.util.Arrays;
import java.util.Random;

/** Rendering-independent ten-by-twenty falling-block game. */
public final class TetrisGame {
	public static final int WIDTH = 10;
	public static final int HEIGHT = 20;
	private static final int[][][][] SHAPES = {
		{{{0,1},{1,1},{2,1},{3,1}}, {{2,0},{2,1},{2,2},{2,3}}},
		{{{1,0},{2,0},{1,1},{2,1}}},
		{{{1,0},{0,1},{1,1},{2,1}}, {{1,0},{1,1},{2,1},{1,2}}, {{0,1},{1,1},{2,1},{1,2}}, {{1,0},{0,1},{1,1},{1,2}}},
		{{{1,0},{2,0},{0,1},{1,1}}, {{1,0},{1,1},{2,1},{2,2}}},
		{{{0,0},{1,0},{1,1},{2,1}}, {{2,0},{1,1},{2,1},{1,2}}},
		{{{0,0},{0,1},{1,1},{2,1}}, {{1,0},{2,0},{1,1},{1,2}}, {{0,1},{1,1},{2,1},{2,2}}, {{1,0},{1,1},{0,2},{1,2}}},
		{{{2,0},{0,1},{1,1},{2,1}}, {{1,0},{1,1},{1,2},{2,2}}, {{0,1},{1,1},{2,1},{0,2}}, {{0,0},{1,0},{1,1},{1,2}}}
	};
	private static final int[] COLORS = {0xFF20DDE8, 0xFFFFD54F, 0xFFAB47BC, 0xFF66BB6A,
		0xFFEF5350, 0xFF42A5F5, 0xFFFF8A3D};

	private final Random random;
	private final int[][] board = new int[HEIGHT][WIDTH];
	private int piece;
	private int rotation;
	private int pieceX;
	private int pieceY;
	private int score;
	private int lines;
	private boolean over;

	public TetrisGame(long seed) {
		random = new Random(seed);
		reset();
	}

	public void reset() {
		for (int[] row : board) Arrays.fill(row, 0);
		score = 0;
		lines = 0;
		over = false;
		spawnPiece();
	}

	public boolean move(int dx) {
		if (!over && fits(pieceX + dx, pieceY, rotation)) {
			pieceX += dx;
			return true;
		}
		return false;
	}

	public boolean rotate() {
		if (over) return false;
		int next = (rotation + 1) % SHAPES[piece].length;
		for (int kick : new int[]{0, -1, 1, -2, 2}) {
			if (fits(pieceX + kick, pieceY, next)) {
				pieceX += kick;
				rotation = next;
				return true;
			}
		}
		return false;
	}

	public void softDrop() {
		if (!over && fits(pieceX, pieceY + 1, rotation)) {
			pieceY++;
			score++;
		} else lockPiece();
	}

	public void tick() {
		if (!over && fits(pieceX, pieceY + 1, rotation)) pieceY++;
		else lockPiece();
	}

	public void hardDrop() {
		if (over) return;
		int distance = 0;
		while (fits(pieceX, pieceY + 1, rotation)) {
			pieceY++;
			distance++;
		}
		score += distance * 2;
		lockPiece();
	}

	private void lockPiece() {
		if (over) return;
		for (int[] cell : shape()) {
			int x = pieceX + cell[0];
			int y = pieceY + cell[1];
			if (y >= 0 && y < HEIGHT && x >= 0 && x < WIDTH) board[y][x] = piece + 1;
		}
		clearLines();
		spawnPiece();
	}

	private void clearLines() {
		int cleared = 0;
		for (int y = HEIGHT - 1; y >= 0; y--) {
			boolean full = true;
			for (int x = 0; x < WIDTH; x++) if (board[y][x] == 0) full = false;
			if (!full) continue;
			cleared++;
			for (int pull = y; pull > 0; pull--) board[pull] = Arrays.copyOf(board[pull - 1], WIDTH);
			board[0] = new int[WIDTH];
			y++;
		}
		if (cleared > 0) {
			lines += cleared;
			score += switch (cleared) { case 1 -> 100; case 2 -> 300; case 3 -> 500; default -> 800; };
		}
	}

	private void spawnPiece() {
		piece = random.nextInt(SHAPES.length);
		rotation = 0;
		pieceX = 3;
		pieceY = 0;
		if (!fits(pieceX, pieceY, rotation)) over = true;
	}

	private boolean fits(int x, int y, int candidateRotation) {
		for (int[] cell : SHAPES[piece][candidateRotation]) {
			int boardX = x + cell[0];
			int boardY = y + cell[1];
			if (boardX < 0 || boardX >= WIDTH || boardY >= HEIGHT) return false;
			if (boardY >= 0 && board[boardY][boardX] != 0) return false;
		}
		return true;
	}

	private int[][] shape() { return SHAPES[piece][rotation]; }

	public int cell(int x, int y) {
		int settled = board[y][x];
		if (settled != 0) return COLORS[settled - 1];
		for (int[] cell : shape()) {
			if (pieceX + cell[0] == x && pieceY + cell[1] == y) return COLORS[piece];
		}
		return 0;
	}

	public int score() { return score; }
	public int lines() { return lines; }
	public int level() { return 1 + lines / 10; }
	public boolean isOver() { return over; }
}
