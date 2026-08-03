package com.example.ai_companion.client.minigame;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Minecraft-block Minesweeper screen with server-authoritative mineral rewards. */
public final class MinesweeperScreen extends Screen {
	private static final int[] NUMBER_COLORS = {
		0xFFFFFFFF, 0xFF42A5F5, 0xFF66BB6A, 0xFFEF5350, 0xFF7E57C2,
		0xFFFF7043, 0xFF26C6DA, 0xFF263238, 0xFF78909C
	};

	private final Screen parent;
	private final MinigameProgress progress;
	private MinesweeperGame.Difficulty difficulty = MinesweeperGame.Difficulty.BEGINNER;
	private MinesweeperGame game = new MinesweeperGame(difficulty);
	private boolean flagMode;
	private boolean resultRecorded;
	private boolean rewardSessionStarted;
	private String sessionId = newSessionId();
	private String status = "左键翻开 · 右键插旗 · 第一次翻开必定安全";
	private int boardX;
	private int boardY;
	private int cellSize;

	public MinesweeperScreen(Screen parent, MinigameProgress progress) {
		super(Component.literal("Minecraft 方块扫雷"));
		this.parent = parent;
		this.progress = progress;
	}

	@Override
	protected void init() {
		calculateBoardGeometry();
		int buttonY = height - 25;
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(10, buttonY, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("难度：" + difficulty.displayName()),
			button -> cycleDifficulty()).bounds(width / 2 - 175, buttonY, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("切换翻开/插旗"), button -> {
			flagMode = !flagMode;
			status = flagMode ? "当前操作：插旗（也可直接右键格子）" : "当前操作：翻开";
		}).bounds(width / 2 - 55, buttonY, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("重新开始"), button -> restart())
			.bounds(width / 2 + 85, buttonY, 100, 20).build());
	}

	@Override
	public void tick() {
		game.tick();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int x = (int) ((event.x() - boardX) / cellSize);
		int y = (int) ((event.y() - boardY) / cellSize);
		if (event.x() < boardX || event.y() < boardY || x < 0 || x >= game.width()
				|| y < 0 || y >= game.height()) return false;
		if (event.button() == 1 || flagMode) {
			game.toggleFlag(x, y);
			return true;
		}
		if (event.button() != 0) return false;
		if (game.state() == MinesweeperGame.State.READY) startRewardSession();
		handleResult(game.reveal(x, y));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_R) {
			restart();
			return true;
		}
		if (event.key() == InputConstants.KEY_F) {
			flagMode = !flagMode;
			status = flagMode ? "当前操作：插旗" : "当前操作：翻开";
			return true;
		}
		return super.keyPressed(event);
	}

	private void handleResult(MinesweeperGame.ActionResult result) {
		if (result == MinesweeperGame.ActionResult.EXPLODED) {
			recordResult(false);
			status = "踩到 TNT！按 R 或点击重新开始";
		} else if (result == MinesweeperGame.ActionResult.WON) {
			recordResult(true);
			status = "扫雷成功！矿物奖励结果会显示在聊天栏";
		}
	}

	private void recordResult(boolean won) {
		if (resultRecorded) return;
		resultRecorded = true;
		progress.recordMinesweeperResult(won, game.elapsedTicks(), difficulty);
		if (!won || !rewardSessionStarted || minecraft == null || minecraft.getConnection() == null) return;
		minecraft.getConnection().sendCommand("aiplayer minigame finish minesweeper " + sessionId + " "
			+ game.elapsedTicks());
	}

	private void startRewardSession() {
		if (rewardSessionStarted) return;
		rewardSessionStarted = true;
		if (minecraft != null && minecraft.getConnection() != null) {
			minecraft.getConnection().sendCommand("aiplayer minigame start minesweeper " + sessionId);
		}
	}

	private void restart() {
		game = new MinesweeperGame(difficulty);
		flagMode = false;
		resultRecorded = false;
		rewardSessionStarted = false;
		sessionId = newSessionId();
		status = "左键翻开 · 右键插旗 · 第一次翻开必定安全";
		calculateBoardGeometry();
	}

	private void cycleDifficulty() {
		MinesweeperGame.Difficulty[] difficulties = MinesweeperGame.Difficulty.values();
		difficulty = difficulties[(difficulty.ordinal() + 1) % difficulties.length];
		restart();
		rebuildWidgets();
	}

	private static String newSessionId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private void calculateBoardGeometry() {
		cellSize = Math.max(8, Math.min(22, Math.min((width - 30) / game.width(),
			(height - 105) / game.height())));
		boardX = (width - cellSize * game.width()) / 2;
		boardY = 42;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		calculateBoardGeometry();
		graphics.fill(0, 0, width, height, 0xE612171B);
		int boardWidth = cellSize * game.width();
		int boardHeight = cellSize * game.height();
		graphics.fill(boardX - 3, boardY - 3, boardX + boardWidth + 3, boardY + boardHeight + 3,
			0xFF263238);
		for (int y = 0; y < game.height(); y++) {
			for (int x = 0; x < game.width(); x++) drawCell(graphics, x, y);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "Minecraft 方块扫雷 · " + difficulty.displayName() + " · "
			+ game.width() + "×" + game.height() + " · " + game.mineCount() + " 个 TNT", width / 2, 10,
			0xFFFFFFFF);
		graphics.text(font, "剩余 TNT 估计：" + game.remainingMineEstimate(), boardX, 27, 0xFFFFD54F);
		graphics.text(font, "用时：" + formatTicks(game.elapsedTicks()), boardX + boardWidth - 80, 27,
			0xFFB0BEC5);
		graphics.centeredText(font, status, width / 2, height - 43, 0xFFA5D6A7);
		graphics.text(font, "本难度最佳 " + progress.minesweeperBestTime(difficulty) + " · 胜场 "
			+ progress.minesweeperWins + " · 最佳连胜 " + progress.minesweeperBestStreak,
			10, height - 56, 0xFFB0BEC5);
	}

	private void drawCell(GuiGraphicsExtractor graphics, int x, int y) {
		MinesweeperGame.Cell cell = game.cell(x, y);
		int left = boardX + x * cellSize;
		int top = boardY + y * cellSize;
		boolean revealMine = cell.mine() && (game.state() == MinesweeperGame.State.LOST
			|| game.state() == MinesweeperGame.State.WON);
		if (!cell.revealed() && !revealMine) {
			int color = cell.flagged() ? 0xFFB71C1C : 0xFF546E7A;
			graphics.fill(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1, color);
			graphics.horizontalLine(left + 2, left + cellSize - 2, top + 2, 0x66FFFFFF);
			if (cell.flagged()) graphics.centeredText(font, "⚑", left + cellSize / 2,
				top + (cellSize - 8) / 2, 0xFFFFFFFF);
			return;
		}
		if (cell.mine()) {
			graphics.fill(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1, 0xFFD32F2F);
			graphics.centeredText(font, "T", left + cellSize / 2, top + (cellSize - 8) / 2, 0xFFFFFFFF);
			return;
		}
		graphics.fill(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1, 0xFFB0BEC5);
		if (cell.adjacentMines() > 0) {
			graphics.centeredText(font, Integer.toString(cell.adjacentMines()), left + cellSize / 2,
				top + (cellSize - 8) / 2, NUMBER_COLORS[cell.adjacentMines()]);
		}
	}

	private static String formatTicks(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		return "%02d:%02d".formatted(seconds / 60, seconds % 60);
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
