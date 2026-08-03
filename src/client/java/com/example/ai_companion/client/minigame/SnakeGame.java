package com.example.ai_companion.client.minigame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Pure game-state implementation for the pixel Snake minigame. */
public final class SnakeGame {
	public static final int WIDTH = 24;
	public static final int HEIGHT = 18;

	public enum Direction {
		UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

		private final int dx;
		private final int dy;

		Direction(int dx, int dy) {
			this.dx = dx;
			this.dy = dy;
		}

		boolean isOpposite(Direction other) {
			return dx + other.dx == 0 && dy + other.dy == 0;
		}
	}

	public enum FoodType {
		APPLE("苹果", 1, 0xFFE53935),
		GOLDEN_APPLE("金苹果", 3, 0xFFFFC107),
		DIAMOND("钻石", 5, 0xFF4DD0E1);

		private final String displayName;
		private final int points;
		private final int color;

		FoodType(String displayName, int points, int color) {
			this.displayName = displayName;
			this.points = points;
			this.color = color;
		}

		public String displayName() {
			return displayName;
		}

		public int points() {
			return points;
		}

		public int color() {
			return color;
		}
	}

	public enum State { RUNNING, PAUSED, GAME_OVER, WON }

	public enum TickResult { MOVED, ATE, GAME_OVER, WON, NO_CHANGE }

	public record Cell(int x, int y) {
		Cell move(Direction direction) {
			return new Cell(x + direction.dx, y + direction.dy);
		}
	}

	public record Food(Cell cell, FoodType type) {
		public Food {
			Objects.requireNonNull(cell, "cell");
			Objects.requireNonNull(type, "type");
		}
	}

	private final Random random;
	private final Deque<Cell> snake = new ArrayDeque<>();
	private Direction direction;
	private Direction queuedDirection;
	private Food food;
	private State state;
	private int score;

	public SnakeGame() {
		this(new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public SnakeGame(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		reset();
	}

	public void reset() {
		snake.clear();
		int centerX = WIDTH / 2;
		int centerY = HEIGHT / 2;
		snake.addLast(new Cell(centerX, centerY));
		snake.addLast(new Cell(centerX - 1, centerY));
		snake.addLast(new Cell(centerX - 2, centerY));
		direction = Direction.RIGHT;
		queuedDirection = Direction.RIGHT;
		score = 0;
		state = State.RUNNING;
		spawnFood();
	}

	public void queueDirection(Direction next) {
		Objects.requireNonNull(next, "next");
		// Accept at most one turn between movement ticks. This prevents a rapid RIGHT -> UP -> LEFT
		// sequence from becoming an immediate reversal before the snake has actually moved upward.
		if (state == State.RUNNING && queuedDirection == direction && !next.isOpposite(direction)) {
			queuedDirection = next;
		}
	}

	public void togglePause() {
		if (state == State.RUNNING) state = State.PAUSED;
		else if (state == State.PAUSED) state = State.RUNNING;
	}

	public TickResult tick() {
		if (state != State.RUNNING) return TickResult.NO_CHANGE;
		direction = queuedDirection;
		Cell next = snake.getFirst().move(direction);
		boolean ate = next.equals(food.cell());
		Cell tail = snake.getLast();
		boolean outside = next.x() < 0 || next.x() >= WIDTH || next.y() < 0 || next.y() >= HEIGHT;
		boolean hitBody = snake.contains(next) && (ate || !next.equals(tail));
		if (outside || hitBody) {
			state = State.GAME_OVER;
			return TickResult.GAME_OVER;
		}

		snake.addFirst(next);
		if (!ate) {
			snake.removeLast();
			return TickResult.MOVED;
		}

		score += food.type().points();
		if (snake.size() == WIDTH * HEIGHT) {
			state = State.WON;
			return TickResult.WON;
		}
		spawnFood();
		return TickResult.ATE;
	}

	private void spawnFood() {
		List<Cell> empty = new ArrayList<>(WIDTH * HEIGHT - snake.size());
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				Cell candidate = new Cell(x, y);
				if (!snake.contains(candidate)) empty.add(candidate);
			}
		}
		if (empty.isEmpty()) return;
		int roll = random.nextInt(100);
		FoodType type = roll < 70 ? FoodType.APPLE : roll < 92
			? FoodType.GOLDEN_APPLE : FoodType.DIAMOND;
		food = new Food(empty.get(random.nextInt(empty.size())), type);
	}

	public int movementIntervalTicks() {
		return Math.max(2, 7 - score / 10);
	}

	public List<Cell> snake() {
		return List.copyOf(snake);
	}

	public Direction direction() {
		return direction;
	}

	public Food food() {
		return food;
	}

	public State state() {
		return state;
	}

	public int score() {
		return score;
	}
}
