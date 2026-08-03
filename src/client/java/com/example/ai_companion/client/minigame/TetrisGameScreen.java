package com.example.ai_companion.client.minigame;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Playable Minecraft-themed falling-block game. */
public final class TetrisGameScreen extends Screen {
	private static final int CELL = 13;
	private final Screen parent;
	private final MinigameScores scores;
	private final TetrisGame game = new TetrisGame(System.nanoTime());
	private int tickCounter;
	private boolean resultSaved;

	public TetrisGameScreen(Screen parent, MinigameScores scores) {
		super(Component.literal("Minecraft 俄罗斯方块"));
		this.parent = parent;
		this.scores = scores;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("重新开始"), button -> restart())
			.bounds(width / 2 + 20, height / 2 - 80, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(width / 2 + 20, height / 2 - 53, 120, 20).build());
	}

	@Override
	public void tick() {
		if (game.isOver()) {
			saveResult();
			return;
		}
		if (++tickCounter >= Math.max(2, 12 - game.level())) {
			tickCounter = 0;
			game.tick();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case 263, 65 -> game.move(-1);
			case 262, 68 -> game.move(1);
			case 265, 87 -> game.rotate();
			case 264, 83 -> game.softDrop();
			case 32 -> { if (game.isOver()) restart(); else game.hardDrop(); }
			default -> { return super.keyPressed(event); }
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int boardWidth = TetrisGame.WIDTH * CELL;
		int boardHeight = TetrisGame.HEIGHT * CELL;
		int left = width / 2 - boardWidth / 2 - 52;
		int top = height / 2 - boardHeight / 2;
		graphics.fill(left - 2, top - 2, left + boardWidth + 2, top + boardHeight + 2, 0xFF8B8B8B);
		graphics.fill(left, top, left + boardWidth, top + boardHeight, 0xE50C1420);
		for (int y = 0; y < TetrisGame.HEIGHT; y++) {
			for (int x = 0; x < TetrisGame.WIDTH; x++) {
				int color = game.cell(x, y);
				if (color == 0) continue;
				int px = left + x * CELL;
				int py = top + y * CELL;
				graphics.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, color);
			}
		}

		int infoX = left + boardWidth + 18;
		graphics.text(font, "分数：" + game.score(), infoX, top + 5, 0xFFFFFFFF);
		graphics.text(font, "消除：" + game.lines(), infoX, top + 22, 0xFFFFFFFF);
		graphics.text(font, "等级：" + game.level(), infoX, top + 39, 0xFFFFFFFF);
		graphics.text(font, "最高分：" + scores.tetrisHighScore, infoX, top + 63, 0xFFFFD86B);
		graphics.text(font, "← → / A D：移动", infoX, top + 104, 0xFFA8B4C4);
		graphics.text(font, "↑ / W：旋转", infoX, top + 119, 0xFFA8B4C4);
		graphics.text(font, "↓ / S：加速", infoX, top + 134, 0xFFA8B4C4);
		graphics.text(font, "空格：硬降", infoX, top + 149, 0xFFA8B4C4);
		graphics.text(font, "结算矿物：铁 / 金 / 钻石", infoX, top + 178, 0xFF9AD5FF);
		if (game.isOver()) {
			graphics.centeredText(font, "游戏结束 · 空格重新开始", left + boardWidth / 2,
				top + boardHeight / 2, 0xFFFF6B6B);
		}
	}

	private void saveResult() {
		if (resultSaved) return;
		scores.recordTetris(game.score(), game.lines());
		if (minecraft != null && minecraft.getConnection() != null && game.lines() > 0) {
			minecraft.getConnection().sendCommand("aiplayer minigame reward tetris "
				+ game.score() + " " + game.lines());
		}
		resultSaved = true;
	}

	private void restart() {
		game.reset();
		tickCounter = 0;
		resultSaved = false;
	}

	@Override
	public void onClose() {
		saveResult();
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
