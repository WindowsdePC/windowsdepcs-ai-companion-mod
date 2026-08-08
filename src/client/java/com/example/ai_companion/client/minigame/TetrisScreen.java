package com.example.ai_companion.client.minigame;

import com.example.ai_companion.client.ClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Playable Tetris screen with server-authoritative mineral reward delivery. */
public final class TetrisScreen extends Screen {
	private final Screen parent;
	private final MinigameProgress progress;
	private final ClientSettings settings;
	private final TetrisGame game = new TetrisGame();
	private Button pauseButton;
	private int dropCounter;
	private boolean resultRecorded;
	private boolean sessionStarted;
	private String sessionId = newSessionId();
	private String rewardStatus = "消除至少一行，结束后可获得随机矿物";

	public TetrisScreen(Screen parent, MinigameProgress progress, ClientSettings settings) {
		super(Component.literal("Minecraft 俄罗斯方块"));
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
		if (!sessionStarted) startRewardSession();
	}

	@Override
	public void tick() {
		if (game.state() != TetrisGame.State.RUNNING) return;
		dropCounter++;
		if (dropCounter < game.automaticDropIntervalTicks()) return;
		dropCounter = 0;
		handleStep(game.tick());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == settings.minigameLeftCode()) {
			game.moveLeft();
			return true;
		}
		if (key == settings.minigameRightCode()) {
			game.moveRight();
			return true;
		}
		if (key == settings.minigameDownCode()) {
			handleStep(game.softDrop());
			return true;
		}
		if (key == settings.minigameUpCode()) {
			game.rotateClockwise();
			return true;
		}
		if (key == settings.minigameActionCode()) {
			handleStep(game.hardDrop());
			return true;
		}
		if (key == settings.minigamePauseCode()) {
			togglePause();
			return true;
		}
		if (key == settings.minigameRestartCode()) {
			restart();
			return true;
		}
		return super.keyPressed(event);
	}

	private void handleStep(TetrisGame.StepResult result) {
		if (result.gameOver() || game.state() == TetrisGame.State.GAME_OVER) recordResult();
	}

	private void togglePause() {
		game.togglePause();
		if (pauseButton != null) pauseButton.setMessage(pauseLabel());
	}

	private Component pauseLabel() {
		return Component.literal(game.state() == TetrisGame.State.PAUSED ? "继续" : "暂停");
	}

	private void restart() {
		game.reset();
		dropCounter = 0;
		resultRecorded = false;
		sessionStarted = false;
		sessionId = newSessionId();
		rewardStatus = "消除至少一行，结束后可获得随机矿物";
		if (pauseButton != null) pauseButton.setMessage(pauseLabel());
		startRewardSession();
	}

	private void recordResult() {
		if (resultRecorded) return;
		resultRecorded = true;
		progress.recordTetrisResult(game.score(), game.lines());
		if (minecraft == null || minecraft.getConnection() == null) {
			rewardStatus = "本局记录已保存；当前没有服务器连接，无法发放矿物";
			return;
		}
		com.example.ai_companion.client.UiActionClient.send("minigame.tetris.finish", sessionId,
			Integer.toString(game.score()), Integer.toString(game.lines()));
		rewardStatus = game.lines() > 0 ? "已提交成绩；矿物奖励结果会显示在物品栏上方"
			: "未消除方块行，本局没有矿物奖励";
	}

	private void startRewardSession() {
		if (sessionStarted) return;
		sessionStarted = true;
		if (minecraft != null && minecraft.getConnection() != null) {
			com.example.ai_companion.client.UiActionClient.send("minigame.tetris.start", sessionId);
		}
	}

	private static String newSessionId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		int cellSize = Math.max(6, Math.min(14, Math.min((height - 72) / TetrisGame.HEIGHT,
			Math.max(6, (width - 190) / TetrisGame.WIDTH))));
		int boardWidth = cellSize * TetrisGame.WIDTH;
		int boardHeight = cellSize * TetrisGame.HEIGHT;
		int boardX = Math.max(10, (width - boardWidth - 150) / 2);
		int boardY = 34;
		graphics.fill(boardX - 2, boardY - 2, boardX + boardWidth + 2, boardY + boardHeight + 2,
			0xFF90A4AE);
		graphics.fill(boardX, boardY, boardX + boardWidth, boardY + boardHeight, 0xFF0D141A);

		List<TetrisGame.Cell> ghost = game.ghostCells();
		for (TetrisGame.Cell cell : ghost) {
			if (cell.y() >= 0) fillBlock(graphics, boardX, boardY, cellSize, cell.x(), cell.y(),
				(cell.type().color() & 0x00FFFFFF) | 0x44000000);
		}
		for (int y = 0; y < TetrisGame.HEIGHT; y++) {
			for (int x = 0; x < TetrisGame.WIDTH; x++) {
				int color = game.cellColor(x, y);
				if (color != 0) fillBlock(graphics, boardX, boardY, cellSize, x, y, color);
				else graphics.outline(boardX + x * cellSize, boardY + y * cellSize, cellSize, cellSize,
					0x281F2B33);
			}
		}

		int sideX = boardX + boardWidth + 18;
		graphics.text(font, "分数  " + game.score(), sideX, boardY, 0xFFFFFFFF);
		graphics.text(font, "最高  " + progress.tetrisHighScore, sideX, boardY + 14, 0xFFB0BEC5);
		graphics.text(font, "消行  " + game.lines(), sideX, boardY + 32, 0xFFFFFFFF);
		graphics.text(font, "等级  " + game.level(), sideX, boardY + 46, 0xFFFFFFFF);
		graphics.text(font, "下一个", sideX, boardY + 70, 0xFFFFD54F);
		for (TetrisGame.Cell cell : game.nextCells()) {
			int previewSize = Math.max(6, Math.min(10, cellSize));
			graphics.fill(sideX + cell.x() * previewSize, boardY + 86 + cell.y() * previewSize,
				sideX + (cell.x() + 1) * previewSize - 1,
				boardY + 86 + (cell.y() + 1) * previewSize - 1, cell.type().color());
		}
		graphics.text(font, "←/→ 或 A/D：移动", sideX, boardY + 135, 0xFFB0BEC5);
		graphics.text(font, "↑/W/X：旋转", sideX, boardY + 147, 0xFFB0BEC5);
		graphics.text(font, "↓/S：软降", sideX, boardY + 159, 0xFFB0BEC5);
		graphics.text(font, "空格：硬降", sideX, boardY + 171, 0xFFB0BEC5);
		graphics.text(font, "P 暂停 · R 重开", sideX, boardY + 183, 0xFFB0BEC5);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "Minecraft 俄罗斯方块", width / 2, 10, 0xFFFFFFFF);
		graphics.centeredText(font, rewardStatus, width / 2, height - 39, 0xFFA5D6A7);
		if (game.state() == TetrisGame.State.PAUSED) {
			drawOverlay(graphics, boardX, boardY, boardWidth, boardHeight, "已暂停");
		} else if (game.state() == TetrisGame.State.GAME_OVER) {
			drawOverlay(graphics, boardX, boardY, boardWidth, boardHeight, "游戏结束 · R 重新开始");
		}
	}

	private static void fillBlock(GuiGraphicsExtractor graphics, int boardX, int boardY,
			int size, int x, int y, int color) {
		int left = boardX + x * size;
		int top = boardY + y * size;
		graphics.fill(left + 1, top + 1, left + size - 1, top + size - 1, color);
		graphics.horizontalLine(left + 2, left + size - 2, top + 2, 0x66FFFFFF);
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
