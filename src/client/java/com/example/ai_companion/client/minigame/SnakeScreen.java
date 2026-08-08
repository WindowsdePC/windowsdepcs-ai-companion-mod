package com.example.ai_companion.client.minigame;

import com.example.ai_companion.client.ClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Keyboard-driven pixel Snake screen opened from the unified minigame center. */
public final class SnakeScreen extends Screen {
	private final Screen parent;
	private final MinigameProgress progress;
	private final ClientSettings settings;
	private final SnakeGame game = new SnakeGame();
	private Button pauseButton;
	private int tickCounter;
	private boolean resultRecorded;
	private String unlockMessage = "";

	public SnakeScreen(Screen parent, MinigameProgress progress, ClientSettings settings) {
		super(Component.literal("贪吃蛇"));
		this.parent = parent;
		this.progress = progress;
		this.settings = settings;
	}

	@Override
	protected void init() {
		int buttonY = height - 25;
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(10, buttonY, 130, 20).build());
		pauseButton = addRenderableWidget(Button.builder(pauseLabel(), button -> togglePause())
			.bounds(width / 2 - 105, buttonY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("重新开始"), button -> restart())
			.bounds(width / 2 + 5, buttonY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("蛇皮肤：" + progress.selectedSnakeSkin.displayName()),
			button -> cycleSkin(button)).bounds(width - 160, buttonY, 150, 20).build());
	}

	@Override
	public void tick() {
		if (game.state() != SnakeGame.State.RUNNING) return;
		tickCounter++;
		if (tickCounter < game.movementIntervalTicks()) return;
		tickCounter = 0;
		SnakeGame.TickResult result = game.tick();
		if (result == SnakeGame.TickResult.GAME_OVER || result == SnakeGame.TickResult.WON) {
			recordResult();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == settings.minigameUpCode()) {
			game.queueDirection(SnakeGame.Direction.UP);
			return true;
		}
		if (key == settings.minigameDownCode()) {
			game.queueDirection(SnakeGame.Direction.DOWN);
			return true;
		}
		if (key == settings.minigameLeftCode()) {
			game.queueDirection(SnakeGame.Direction.LEFT);
			return true;
		}
		if (key == settings.minigameRightCode()) {
			game.queueDirection(SnakeGame.Direction.RIGHT);
			return true;
		}
		if (key == settings.minigamePauseCode() || key == settings.minigameActionCode()) {
			togglePause();
			return true;
		}
		if (key == settings.minigameRestartCode()) {
			restart();
			return true;
		}
		return super.keyPressed(event);
	}

	private void togglePause() {
		game.togglePause();
		if (pauseButton != null) pauseButton.setMessage(pauseLabel());
	}

	private Component pauseLabel() {
		return Component.literal(game.state() == SnakeGame.State.PAUSED ? "继续" : "暂停");
	}

	private void restart() {
		game.reset();
		tickCounter = 0;
		resultRecorded = false;
		unlockMessage = "";
		if (pauseButton != null) pauseButton.setMessage(pauseLabel());
	}

	private void cycleSkin(Button button) {
		MinigameProgress.SnakeSkin skin = progress.cycleSnakeSkin();
		button.setMessage(Component.literal("蛇皮肤：" + skin.displayName()));
	}

	private void recordResult() {
		if (resultRecorded) return;
		resultRecorded = true;
		List<String> unlocks = progress.recordSnakeScore(game.score());
		unlockMessage = String.join(" · ", unlocks);
		if (unlockMessage.isBlank()) unlockMessage = "本局记录已保存";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		int cellSize = Math.max(5, Math.min(16, Math.min((width - 30) / SnakeGame.WIDTH,
			(height - 100) / SnakeGame.HEIGHT)));
		int boardWidth = cellSize * SnakeGame.WIDTH;
		int boardHeight = cellSize * SnakeGame.HEIGHT;
		int boardX = (width - boardWidth) / 2;
		int boardY = 36;
		graphics.fill(boardX - 2, boardY - 2, boardX + boardWidth + 2, boardY + boardHeight + 2,
			0xFF78909C);
		graphics.fill(boardX, boardY, boardX + boardWidth, boardY + boardHeight, 0xFF101820);
		for (int x = 1; x < SnakeGame.WIDTH; x++) {
			graphics.verticalLine(boardX + x * cellSize, boardY, boardY + boardHeight, 0x241B2B34);
		}
		for (int y = 1; y < SnakeGame.HEIGHT; y++) {
			graphics.horizontalLine(boardX, boardX + boardWidth, boardY + y * cellSize, 0x241B2B34);
		}

		SnakeGame.Food food = game.food();
		fillCell(graphics, boardX, boardY, cellSize, food.cell(), food.type().color(), 2);
		List<SnakeGame.Cell> snake = game.snake();
		for (int index = snake.size() - 1; index >= 0; index--) {
			int color = index == 0 ? progress.selectedSnakeSkin.headColor()
				: progress.selectedSnakeSkin.bodyColor();
			fillCell(graphics, boardX, boardY, cellSize, snake.get(index), color, 1);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "贪吃蛇 · 分数 " + game.score() + " · 最高 " + progress.snakeHighScore,
			width / 2, 10, 0xFFFFFF);
		graphics.text(font, "食物：苹果 +1 / 金苹果 +3 / 钻石 +5", 10, height - 48, 0xFFB0BEC5);
		graphics.text(font, "方向键或 WASD 移动 · 空格/P 暂停 · R 重开", 10, height - 37, 0xFFB0BEC5);
		graphics.text(font, "称号：" + progress.snakeTitle(), Math.max(10, width - 190), height - 48,
			progress.snakeMasterTitleUnlocked ? 0xFFFFD54F : 0xFF90A4AE);
		if (game.state() == SnakeGame.State.PAUSED) {
			drawOverlay(graphics, boardX, boardY, boardWidth, boardHeight, "已暂停");
		} else if (game.state() == SnakeGame.State.GAME_OVER) {
			drawOverlay(graphics, boardX, boardY, boardWidth, boardHeight, "游戏结束 · " + unlockMessage);
		} else if (game.state() == SnakeGame.State.WON) {
			drawOverlay(graphics, boardX, boardY, boardWidth, boardHeight, "棋盘已吃满！ · " + unlockMessage);
		}
	}

	private static void fillCell(GuiGraphicsExtractor graphics, int boardX, int boardY, int cellSize,
			SnakeGame.Cell cell, int color, int inset) {
		int x = boardX + cell.x() * cellSize;
		int y = boardY + cell.y() * cellSize;
		graphics.fill(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset, color);
	}

	private void drawOverlay(GuiGraphicsExtractor graphics, int x, int y, int boardWidth,
			int boardHeight, String message) {
		graphics.fill(x, y + boardHeight / 2 - 18, x + boardWidth, y + boardHeight / 2 + 18, 0xD9000000);
		graphics.centeredText(font, message, x + boardWidth / 2, y + boardHeight / 2 - 4, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
