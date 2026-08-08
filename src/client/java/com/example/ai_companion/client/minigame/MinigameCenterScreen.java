package com.example.ai_companion.client.minigame;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A real local popup that launches every bundled, offline-capable minigame. */
public final class MinigameCenterScreen extends Screen {
	private final Screen parent;
	private final MinigameProgress progress;

	public MinigameCenterScreen(Screen parent, MinigameProgress progress) {
		super(Component.literal("小游戏中心 · 纯本地运行"));
		this.parent = parent;
		this.progress = progress;
	}

	@Override
	protected void init() {
		int width = Math.min(520, this.width - 40);
		int left = (this.width - width) / 2;
		int half = (width - 10) / 2;
		addRenderableWidget(Button.builder(Component.literal("贪吃蛇"), b ->
			minecraft.setScreenAndShow(new SnakeScreen(this, progress))).bounds(left, 62, half, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Minecraft 俄罗斯方块"), b ->
			minecraft.setScreenAndShow(new TetrisScreen(this, progress))).bounds(left + half + 10, 62, half, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Minecraft 方块扫雷"), b ->
			minecraft.setScreenAndShow(new MinesweeperScreen(this, progress))).bounds(left, 94, half, 22).build());
		addRenderableWidget(Button.builder(Component.literal("2048"), b ->
			minecraft.setScreenAndShow(new Game2048Screen(this, progress))).bounds(left + half + 10, 94, half, 22).build());
		addRenderableWidget(Button.builder(Component.literal("AI 猜拳"), b ->
			minecraft.setScreenAndShow(new RockPaperScissorsScreen(this, progress)))
			.bounds(left + width / 4, 126, width / 2, 22).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + width / 2 - 55, 176, 110, 20).build());
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 24, 0xFFFFFF);
		graphics.centeredText(font, "游戏逻辑和记录都在当前客户端本地运行；奖励请求由服务器校验", width / 2, 42, 0xA0A0A0);
	}
}
