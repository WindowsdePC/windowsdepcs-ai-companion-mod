package com.example.ai_companion.client.minigame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Deterministic, rendering-independent snake rules. */
public final class SnakeGame {
	public static final int WIDTH = 20;
	public static final int HEIGHT = 16;

	public enum Direction { UP, RIGHT, DOWN, LEFT }
	public enum FoodType {
		APPLE(10, 0xFFE53935), GOLDEN_APPLE(30, 0xFFFFC107), DIAMOND(75, 0xFF32E6E2);

		private final int points;
		private final int color;

		FoodType(int points, int color) {
			this.points = points;
			this.color = color;
		}

		public int points() { return points; }
		public int color() { return color; }
	}

	public record Cell(int x, int y) { }
	public record Food(Cell cell, FoodType type) { }

	private final Random random;
	private final Deque<Cell> body = new ArrayDeque<>();
	private Direction direction = Direction.RIGHT;
	private Direction queuedDirection = Direction.RIGHT;
	private Food food;
	private int score;
	private boolean over;

	public SnakeGame(long seed) {
		random = new Random(seed);
		reset();
	}

	public void reset() {
		body.clear();
		body.addFirst(new Cell(WIDTH / 2, HEIGHT / 2));
		body.addLast(new Cell(WIDTH / 2 - 1, HEIGHT / 2));
		body.addLast(new Cell(WIDTH / 2 - 2, HEIGHT / 2));
		direction = Direction.RIGHT;
		queuedDirection = Direction.RIGHT;
		score = 0;
		over = false;
		spawnFood();
	}

	public void turn(Direction next) {
		if (!isOpposite(direction, next)) queuedDirection = next;
	}

	public void step() {
		if (over) return;
		direction = queuedDirection;
		Cell head = body.getFirst();
		Cell next = switch (direction) {
			case UP -> new Cell(head.x(), head.y() - 1);
			case RIGHT -> new Cell(head.x() + 1, head.y());
			case DOWN -> new Cell(head.x(), head.y() + 1);
			case LEFT -> new Cell(head.x() - 1, head.y());
		};
		boolean eating = next.equals(food.cell());
		Cell tail = body.getLast();
		if (next.x() < 0 || next.x() >= WIDTH || next.y() < 0 || next.y() >= HEIGHT
				|| (body.contains(next) && (eating || !next.equals(tail)))) {
			over = true;
			return;
		}
		body.addFirst(next);
		if (eating) {
			score += food.type().points();
			spawnFood();
		} else {
			body.removeLast();
		}
	}

	private void spawnFood() {
		if (body.size() >= WIDTH * HEIGHT) {
			over = true;
			return;
		}
		Cell candidate;
		do candidate = new Cell(random.nextInt(WIDTH), random.nextInt(HEIGHT));
		while (body.contains(candidate));
		int roll = random.nextInt(100);
		FoodType type = roll < 4 ? FoodType.DIAMOND : roll < 18 ? FoodType.GOLDEN_APPLE : FoodType.APPLE;
		food = new Food(candidate, type);
	}

	private static boolean isOpposite(Direction first, Direction second) {
		return first == Direction.UP && second == Direction.DOWN
			|| first == Direction.DOWN && second == Direction.UP
			|| first == Direction.LEFT && second == Direction.RIGHT
			|| first == Direction.RIGHT && second == Direction.LEFT;
	}

	public List<Cell> body() { return new ArrayList<>(body); }
	public Food food() { return food; }
	public int score() { return score; }
	public boolean isOver() { return over; }
}
