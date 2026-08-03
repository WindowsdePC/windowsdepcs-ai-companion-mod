package com.example.ai_companion.client.minigame;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinigameModelTest {
	@Test
	void snakeMovesAndRejectsImmediateReverse() {
		SnakeGame game = new SnakeGame(new Random(42L));
		SnakeGame.Cell start = game.snake().getFirst();
		game.queueDirection(SnakeGame.Direction.LEFT);
		game.tick();
		assertEquals(start.x() + 1, game.snake().getFirst().x());
		assertEquals(start.y(), game.snake().getFirst().y());
		assertEquals(SnakeGame.State.RUNNING, game.state());
	}

	@Test
	void snakeCanTurnAtRightAngles() {
		SnakeGame game = new SnakeGame(new Random(7L));
		SnakeGame.Cell start = game.snake().getFirst();
		game.queueDirection(SnakeGame.Direction.UP);
		game.tick();
		assertEquals(start.y() - 1, game.snake().getFirst().y());
	}

	@Test
	void snakeAcceptsOnlyOneTurnPerMovementTick() {
		SnakeGame game = new SnakeGame(new Random(9L));
		game.queueDirection(SnakeGame.Direction.UP);
		game.queueDirection(SnakeGame.Direction.LEFT);
		game.tick();
		assertEquals(SnakeGame.HEIGHT / 2 - 1, game.snake().getFirst().y());
	}

	@Test
	void tetrisHardDropAwardsDistanceAndSpawnsNextPiece() {
		TetrisGame game = new TetrisGame(new Random(99L));
		game.hardDrop();
		assertTrue(game.score() > 0);
		assertEquals(TetrisGame.State.RUNNING, game.state());
	}

	@Test
	void tetrisMovementStaysInsideBoard() {
		TetrisGame game = new TetrisGame(new Random(123L));
		for (int index = 0; index < 20; index++) game.moveLeft();
		for (int y = 0; y < TetrisGame.HEIGHT; y++) {
			for (int x = 0; x < TetrisGame.WIDTH; x++) game.cellColor(x, y);
		}
		assertFalse(game.state() == TetrisGame.State.GAME_OVER);
		assertEquals(4, game.ghostCells().size());
	}
}
