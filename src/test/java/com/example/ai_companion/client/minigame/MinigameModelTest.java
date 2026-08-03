package com.example.ai_companion.client.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinigameModelTest {
	@Test
	void snakeMovesAndRejectsImmediateReverse() {
		SnakeGame game = new SnakeGame(42L);
		SnakeGame.Cell start = game.body().getFirst();
		game.turn(SnakeGame.Direction.LEFT);
		game.step();
		assertEquals(start.x() + 1, game.body().getFirst().x());
		assertEquals(start.y(), game.body().getFirst().y());
		assertFalse(game.isOver());
	}

	@Test
	void snakeCanTurnAtRightAngles() {
		SnakeGame game = new SnakeGame(7L);
		SnakeGame.Cell start = game.body().getFirst();
		game.turn(SnakeGame.Direction.UP);
		game.step();
		assertEquals(start.y() - 1, game.body().getFirst().y());
	}

	@Test
	void tetrisHardDropAwardsDistanceAndSpawnsNextPiece() {
		TetrisGame game = new TetrisGame(99L);
		game.hardDrop();
		assertTrue(game.score() > 0);
		assertFalse(game.isOver());
	}

	@Test
	void tetrisMovementStaysInsideBoard() {
		TetrisGame game = new TetrisGame(123L);
		for (int index = 0; index < 20; index++) game.move(-1);
		for (int y = 0; y < TetrisGame.HEIGHT; y++) {
			for (int x = 0; x < TetrisGame.WIDTH; x++) game.cell(x, y);
		}
		assertFalse(game.isOver());
	}
}
