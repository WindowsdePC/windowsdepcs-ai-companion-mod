package com.example.ai_companion.client.minigame;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Playable pixel snake with three Minecraft-themed foods. */
public final class SnakeGameScreen extends Screen {
	private static final int CELL = 14;
	private final Screen parent;
	private final MinigameScores scores;
	private final SnakeGame game = new SnakeGame(System.nanoTime());
	private int tickCounter;
	private boolean resultSaved;

	public SnakeGameScreen(Screen parent, MinigameScores scores) {
		super(Component.literal("贪吃蛇"));
		this.parent = parent;
		this.scores = scores;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("重新开始"), button -> restart())
			.bounds(width / 2 + 58, height / 2 - 65, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(width / 2 + 58, height / 2 - 38, 120, 20).build());
	}

	@Override
	public void tick() {
		if (game.isOver()) {
			saveResult();
			return;
		}
		if (++tickCounter >= Math.max(2, 6 - game.score() / 150)) {
			tickCounter = 0;
			game.step();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case 265, 87 -> game.turn(SnakeGame.Direction.UP);
			case 262, 68 -> game.turn(SnakeGame.Direction.RIGHT);
			case 264, 83 -> game.turn(SnakeGame.Direction.DOWN);
			case 263, 65 -> game.turn(SnakeGame.Direction.LEFT);
			case 32 -> { if (game.isOver()) restart(); }
			default -> { return super.keyPressed(event); }
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int boardWidth = SnakeGame.WIDTH * CELL;
		int boardHeight = SnakeGame.HEIGHT * CELL;
		int left = width / 2 - boardWidth / 2 - 45;
		int top = height / 2 - boardHeight / 2;
		graphics.fill(left - 2, top - 2, left + boardWidth + 2, top + boardHeight + 2, 0xFF8B8B8B);
		graphics.fill(left, top, left + boardWidth, top + boardHeight, 0xE50C1420);
		for (SnakeGame.Cell cell : game.body()) {
			int color = cell.equals(game.body().getFirst()) ? 0xFF8AF06A : 0xFF3BAA4B;
			fillCell(graphics, left, top, cell.x(), cell.y(), color);
		}
		SnakeGame.Food food = game.food();
		fillCell(graphics, left, top, food.cell().x(), food.cell().y(), food.type().color());

		int infoX = left + boardWidth + 18;
		graphics.text(font, "分数：" + game.score(), infoX, top + 5, 0xFFFFFFFF);
		graphics.text(font, "最高分：" + scores.snakeHighScore, infoX, top + 22, 0xFFFFD86B);
		graphics.text(font, "方向键 / WASD", infoX, top + 85, 0xFFA8B4C4);
		graphics.text(font, "红苹果 +10", infoX, top + 106, 0xFFE57373);
		graphics.text(font, "金苹果 +30", infoX, top + 121, 0xFFFFD54F);
		graphics.text(font, "钻石 +75", infoX, top + 136, 0xFF4DE7E1);
		graphics.text(font, scores.snakeMasterTitle ? "称号：贪吃蛇大师 ✓" : "500分解锁称号",
			infoX, top + 166, scores.snakeMasterTitle ? 0xFF8AF06A : 0xFFA8B4C4);
		if (game.isOver()) {
			graphics.centeredText(font, "游戏结束 · 空格重新开始", left + boardWidth / 2,
				top + boardHeight / 2, 0xFFFF6B6B);
		}
	}

	private static void fillCell(GuiGraphicsExtractor graphics, int left, int top, int x, int y, int color) {
		int px = left + x * CELL;
		int py = top + y * CELL;
		graphics.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, color);
	}

	private void saveResult() {
		if (!resultSaved) {
			scores.recordSnake(game.score());
			resultSaved = true;
		}
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
