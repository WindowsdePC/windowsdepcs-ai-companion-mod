package com.example.ai_companion.client.minigame;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

	@Test
	void minesweeperFirstRevealHasSafeNeighborhoodAndCorrectMineCount() {
		MinesweeperGame game = new MinesweeperGame(new Random(2026L));
		game.reveal(7, 5);
		int mines = 0;
		for (int y = 0; y < MinesweeperGame.HEIGHT; y++) {
			for (int x = 0; x < MinesweeperGame.WIDTH; x++) {
				if (game.cell(x, y).mine()) mines++;
			}
		}
		assertEquals(MinesweeperGame.MINE_COUNT, mines);
		for (int y = 4; y <= 6; y++) {
			for (int x = 6; x <= 8; x++) assertFalse(game.cell(x, y).mine());
		}
		assertTrue(game.cell(7, 5).revealed());
	}

	@Test
	void minesweeperWinsAfterAllSafeCellsAreRevealed() {
		MinesweeperGame game = new MinesweeperGame(new Random(77L));
		game.reveal(0, 0);
		for (int y = 0; y < MinesweeperGame.HEIGHT; y++) {
			for (int x = 0; x < MinesweeperGame.WIDTH; x++) {
				if (!game.cell(x, y).mine()) game.reveal(x, y);
			}
		}
		assertEquals(MinesweeperGame.State.WON, game.state());
		assertEquals(MinesweeperGame.WIDTH * MinesweeperGame.HEIGHT - MinesweeperGame.MINE_COUNT,
			game.revealedCount());
	}

	@Test
	void game2048MergesEachTileOnlyOncePerMove() {
		Game2048 game = new Game2048(new Random(15L));
		game.setBoardForTesting(new int[][]{
			{2, 2, 2, 2},
			{0, 0, 0, 0},
			{0, 0, 0, 0},
			{0, 0, 0, 0}
		});
		Game2048.MoveResult result = game.move(Game2048.Direction.LEFT);
		assertTrue(result.changed());
		assertEquals(8, result.scoreGained());
		assertEquals(4, game.valueAt(0, 0));
		assertEquals(4, game.valueAt(1, 0));
	}

	@Test
	void game2048DetectsBoardWithNoAvailableMoves() {
		Game2048 game = new Game2048(new Random(4L));
		game.setBoardForTesting(new int[][]{
			{2, 4, 2, 4},
			{4, 2, 4, 2},
			{2, 4, 2, 4},
			{4, 2, 4, 2}
		});
		Game2048.MoveResult result = game.move(Game2048.Direction.LEFT);
		assertFalse(result.changed());
		assertTrue(result.gameOver());
		assertEquals(Game2048.State.GAME_OVER, game.state());
	}

	@Test
	void rockPaperScissorsResolvesAllOutcomeTypes() {
		assertEquals(RockPaperScissorsGame.Outcome.PLAYER_WIN,
			RockPaperScissorsGame.determineWinner(RockPaperScissorsGame.Choice.ROCK,
				RockPaperScissorsGame.Choice.SCISSORS));
		assertEquals(RockPaperScissorsGame.Outcome.AI_WIN,
			RockPaperScissorsGame.determineWinner(RockPaperScissorsGame.Choice.PAPER,
				RockPaperScissorsGame.Choice.SCISSORS));
		assertEquals(RockPaperScissorsGame.Outcome.DRAW,
			RockPaperScissorsGame.determineWinner(RockPaperScissorsGame.Choice.ROCK,
				RockPaperScissorsGame.Choice.ROCK));
	}

	@Test
	void rockPaperScissorsPersonalitiesAlwaysProduceValidRounds() {
		RockPaperScissorsGame game = new RockPaperScissorsGame(new Random(26L));
		for (int personality = 0; personality < RockPaperScissorsGame.Personality.values().length;
				personality++) {
			for (int round = 0; round < 20; round++) {
				RockPaperScissorsGame.Round result = game.play(RockPaperScissorsGame.Choice.values()[round % 3]);
				assertNotNull(result.aiChoice());
				assertNotNull(result.outcome());
				assertFalse(result.aiMessage().isBlank());
			}
			game.cyclePersonality();
		}
		assertEquals(60, game.rounds().size());
	}
}
