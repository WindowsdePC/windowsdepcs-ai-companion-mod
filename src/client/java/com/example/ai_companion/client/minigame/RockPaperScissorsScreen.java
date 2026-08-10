package com.example.ai_companion.client.minigame;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import com.example.ai_companion.client.ClientSettings;

import java.util.List;

/** Rock-Paper-Scissors screen with a personality-weighted local AI opponent. */
public final class RockPaperScissorsScreen extends Screen {
	private final Screen parent;
	private final MinigameProgress progress;
	private final ClientSettings settings;
	private final RockPaperScissorsGame game = new RockPaperScissorsGame();
	private RockPaperScissorsGame.Round lastRound;

	public RockPaperScissorsScreen(Screen parent, MinigameProgress progress, ClientSettings settings) {
		super(Component.literal("AI 猜拳"));
		this.parent = parent;
		this.progress = progress;
		this.settings = settings;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int choiceWidth = Math.max(80, Math.min(130, (width - 50) / 3));
		int startX = center - (choiceWidth * 3 + 12) / 2;
		addRenderableWidget(choiceButton(RockPaperScissorsGame.Choice.ROCK, startX, 76, choiceWidth));
		addRenderableWidget(choiceButton(RockPaperScissorsGame.Choice.SCISSORS,
			startX + choiceWidth + 6, 76, choiceWidth));
		addRenderableWidget(choiceButton(RockPaperScissorsGame.Choice.PAPER,
			startX + (choiceWidth + 6) * 2, 76, choiceWidth));
		addRenderableWidget(Button.builder(Component.literal("AI 人格：" + game.personality().displayName()),
			button -> {
				game.cyclePersonality();
				rebuildWidgets();
			}).bounds(center - 110, 112, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), button -> onClose())
			.bounds(10, height - 25, 130, 20).build());
	}

	private Button choiceButton(RockPaperScissorsGame.Choice choice, int x, int y, int width) {
		return Button.builder(Component.literal(choice.symbol() + "  " + choice.displayName()),
			button -> play(choice)).bounds(x, y, width, 26).build();
	}

	@Override public boolean keyPressed(KeyEvent event) {
		if (settings.minigameLeftKeyEnabled && event.key() == settings.minigameLeftCode()) {
			play(RockPaperScissorsGame.Choice.ROCK);
			return true;
		}
		if (settings.minigameUpKeyEnabled && event.key() == settings.minigameUpCode()
				|| settings.minigameActionKeyEnabled && event.key() == settings.minigameActionCode()) {
			play(RockPaperScissorsGame.Choice.SCISSORS);
			return true;
		}
		if (settings.minigameRightKeyEnabled && event.key() == settings.minigameRightCode()) {
			play(RockPaperScissorsGame.Choice.PAPER);
			return true;
		}
		return super.keyPressed(event);
	}

	private void play(RockPaperScissorsGame.Choice choice) {
		lastRound = game.play(choice);
		progress.recordRpsRound(lastRound.outcome());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		int panelWidth = Math.min(560, width - 30);
		int left = (width - panelWidth) / 2;
		graphics.fill(left, 36, left + panelWidth, Math.max(150, height - 38), 0x66304455);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "AI 猜拳 · 选择石头、剪刀或布", width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(font, game.personality().description(), width / 2, 50, 0xFFB0BEC5);
		graphics.centeredText(font, "总战绩  胜 " + progress.rpsWins + " / 负 " + progress.rpsLosses
			+ " / 平 " + progress.rpsDraws + "  ·  最佳连胜 " + progress.rpsBestStreak,
			width / 2, 140, 0xFFFFD54F);
		if (lastRound == null) {
			graphics.centeredText(font, "AI 会按照当前人格进行加权随机选择", width / 2, 164,
				0xFFA5D6A7);
		} else {
			String result = switch (lastRound.outcome()) {
				case PLAYER_WIN -> "你赢了";
				case AI_WIN -> "AI 赢了";
				case DRAW -> "平局";
			};
			graphics.centeredText(font, "你出 " + lastRound.playerChoice().displayName() + " · AI 出 "
				+ lastRound.aiChoice().displayName() + " · " + result, width / 2, 162, 0xFFFFFFFF);
			graphics.centeredText(font, "AI：" + lastRound.aiMessage(), width / 2, 178, 0xFFA5D6A7);
		}
		drawHistory(graphics, left + 12, 198);
	}

	private void drawHistory(GuiGraphicsExtractor graphics, int x, int y) {
		List<RockPaperScissorsGame.Round> rounds = game.rounds();
		if (rounds.isEmpty() || y > height - 38) return;
		int start = Math.max(0, rounds.size() - 6);
		StringBuilder line = new StringBuilder("最近：");
		for (int index = start; index < rounds.size(); index++) {
			RockPaperScissorsGame.Round round = rounds.get(index);
			String marker = switch (round.outcome()) {
				case PLAYER_WIN -> "胜";
				case AI_WIN -> "负";
				case DRAW -> "平";
			};
			line.append(' ').append(marker).append('(').append(round.playerChoice().displayName())
				.append('/').append(round.aiChoice().displayName()).append(')');
		}
		graphics.text(font, line.toString(), x, y, 0xFFCFD8DC);
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
