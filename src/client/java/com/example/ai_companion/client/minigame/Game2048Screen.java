package com.example.ai_companion.client.minigame;

import com.example.ai_companion.client.ClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Keyboard-driven 2048 screen using Minecraft-inspired block colors. */
public final class Game2048Screen extends Screen {
	private final Screen parent;
	private final MinigameProgress progress;
	private final ClientSettings settings;
	private final Game2048 game = new Game2048();
	private Button pauseButton;
	private boolean resultRecorded;
	private String status = "方向键或 WASD 移动数字方块";

	public Game2048Screen(Screen parent, MinigameProgress progress, ClientSettings settings) {
		super(Component.literal("Minecraft 2048"));
		this.parent = parent;
		this.progress = progress;
		this.settings = settings;
	}

	@Override
	protected void init() {
		int buttonY = height - 25;
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(10, buttonY, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("撤销一步"), button -> undo())
			.bounds(width / 2 - 160, buttonY, 100, 20).build());
		pauseButton = addRenderableWidget(Button.builder(pauseLabel(), button -> togglePause())
			.bounds(width / 2 - 50, buttonY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("重新开始"), button -> restart())
			.bounds(width / 2 + 60, buttonY, 100, 20).build());
		if (game.state() == Game2048.State.WON) {
			addRenderableWidget(Button.builder(Component.literal("达到 2048，继续挑战"), button -> continueGame())
				.bounds(width - 180, buttonY, 170, 20).build());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == settings.minigameLeftCode()) return move(Game2048.Direction.LEFT);
		if (key == settings.minigameRightCode()) return move(Game2048.Direction.RIGHT);
		if (key == settings.minigameUpCode()) return move(Game2048.Direction.UP);
		if (key == settings.minigameDownCode()) return move(Game2048.Direction.DOWN);
		if (key == settings.minigamePauseCode()) {
			togglePause();
			return true;
		}
		if (key == settings.minigameRestartCode()) {
			restart();
			return true;
		}
		if (key == settings.minigameSecondaryCode()) {
			undo();
			return true;
		}
		if (key == settings.minigameActionCode() && game.state() == Game2048.State.WON) {
			continueGame();
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean move(Game2048.Direction direction) {
		Game2048.MoveResult result = game.move(direction);
		if (result.reached2048()) {
			status = "已合成 2048！按 C 继续挑战，或按 R 开新局";
			rebuildWidgets();
		} else if (result.gameOver()) {
			status = "没有可移动的方块，本局结束";
			recordResult();
		}
		return true;
	}

	private void togglePause() {
		game.togglePause();
		if (pauseButton != null) pauseButton.setMessage(pauseLabel());
	}

	private void undo() {
		if (game.undo()) {
			resultRecorded = false;
			status = "已撤销上一步；每次移动只能撤销一次";
			rebuildWidgets();
		} else {
			status = "当前没有可撤销的步骤";
		}
	}

	private Component pauseLabel() {
		return Component.literal(game.state() == Game2048.State.PAUSED ? "继续" : "暂停");
	}

	private void continueGame() {
		game.continueAfterWin();
		status = game.state() == Game2048.State.GAME_OVER ? "没有可移动的方块，本局结束"
			: "继续挑战 4096 与更高数字";
		rebuildWidgets();
	}

	private void restart() {
		recordResult();
		game.reset();
		resultRecorded = false;
		status = "方向键或 WASD 移动数字方块";
		rebuildWidgets();
	}

	private void recordResult() {
		if (resultRecorded) return;
		resultRecorded = true;
		progress.record2048Result(game.score(), game.bestTile(), game.reached2048());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		int boardSize = Math.max(160, Math.min(360, Math.min(width - 40, height - 92)));
		int cellSize = boardSize / Game2048.SIZE;
		boardSize = cellSize * Game2048.SIZE;
		int boardX = (width - boardSize) / 2;
		int boardY = 37;
		graphics.fill(boardX - 4, boardY - 4, boardX + boardSize + 4, boardY + boardSize + 4,
			0xFF37474F);
		for (int y = 0; y < Game2048.SIZE; y++) {
			for (int x = 0; x < Game2048.SIZE; x++) {
				int value = game.valueAt(x, y);
				int left = boardX + x * cellSize;
				int top = boardY + y * cellSize;
				graphics.fill(left + 3, top + 3, left + cellSize - 3, top + cellSize - 3,
					tileColor(value));
				if (value > 0) graphics.centeredText(font, Integer.toString(value), left + cellSize / 2,
					top + cellSize / 2 - 4, value <= 4 ? 0xFF263238 : 0xFFFFFFFF);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "Minecraft 2048 · 分数 " + game.score() + " · 最高 "
			+ progress.game2048HighScore, width / 2, 10, 0xFFFFFFFF);
		graphics.centeredText(font, status, width / 2, height - 43, 0xFFA5D6A7);
		graphics.text(font, "本局最大 " + game.bestTile() + " · 历史最大 " + progress.game2048BestTile
			+ " · 达成 2048 次数 " + progress.game2048Wins, 10, height - 56, 0xFFB0BEC5);
		if (game.state() == Game2048.State.PAUSED) {
			drawOverlay(graphics, boardX, boardY, boardSize, "已暂停");
		} else if (game.state() == Game2048.State.WON) {
			drawOverlay(graphics, boardX, boardY, boardSize, "成功合成 2048！");
		} else if (game.state() == Game2048.State.GAME_OVER) {
			drawOverlay(graphics, boardX, boardY, boardSize, "游戏结束 · R 重新开始");
		}
	}

	private static int tileColor(int value) {
		return switch (value) {
			case 0 -> 0xFF455A64;
			case 2 -> 0xFFD7CCC8;
			case 4 -> 0xFFFFE0B2;
			case 8 -> 0xFFFFB74D;
			case 16 -> 0xFFFF8A65;
			case 32 -> 0xFFEF5350;
			case 64 -> 0xFFE53935;
			case 128 -> 0xFFFFD54F;
			case 256 -> 0xFFFFCA28;
			case 512 -> 0xFFFFB300;
			case 1024 -> 0xFF7E57C2;
			case 2048 -> 0xFF26A69A;
			default -> 0xFF263238;
		};
	}

	private void drawOverlay(GuiGraphicsExtractor graphics, int x, int y, int size, String message) {
		graphics.fill(x, y + size / 2 - 20, x + size, y + size / 2 + 20, 0xD9000000);
		graphics.centeredText(font, message, x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		recordResult();
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
