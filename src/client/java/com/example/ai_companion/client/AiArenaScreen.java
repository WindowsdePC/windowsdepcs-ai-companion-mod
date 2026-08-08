package com.example.ai_companion.client;

import com.example.ai_companion.arena.ArenaMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-backed arena popup. Buttons use the typed UI channel rather than commands. */
public final class AiArenaScreen extends Screen {
	private final Screen parent;
	private String participants = "AI_1,AI_2";

	public AiArenaScreen(Screen parent) {
		super(Component.literal("AI 竞技场"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panel = Math.min(600, width - 40);
		int left = (width - panel) / 2;
		EditBox names = addRenderableWidget(new EditBox(font, left, 66, panel, 22,
			Component.literal("参赛 AI，逗号分隔")));
		names.setMaxLength(160);
		names.setValue(participants);
		names.setResponder(value -> participants = value);
		int third = (panel - 16) / 3;
		addRenderableWidget(Button.builder(Component.literal("开始 1v1"), b -> start(ArenaMode.ONE_V_ONE))
			.bounds(left, 102, third, 22).build());
		addRenderableWidget(Button.builder(Component.literal("开始 2v2"), b -> start(ArenaMode.TWO_V_TWO))
			.bounds(left + third + 8, 102, third, 22).build());
		addRenderableWidget(Button.builder(Component.literal("开始混战"), b -> start(ArenaMode.FREE_FOR_ALL))
			.bounds(left + (third + 8) * 2, 102, third, 22).build());
		addRenderableWidget(Button.builder(Component.literal("查询状态"), b -> UiActionClient.send("arena.status"))
			.bounds(left, 140, panel / 2 - 5, 22).build());
		addRenderableWidget(Button.builder(Component.literal("停止比赛（管理员）"), b -> UiActionClient.send("arena.stop"))
			.bounds(left + panel / 2 + 5, 140, panel / 2 - 5, 22).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + panel / 2 - 55, 184, 110, 20).build());
	}

	private void start(ArenaMode mode) {
		UiActionClient.send("arena.start", mode.name(), participants);
	}

	@Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 24, 0xFFFFFF);
		graphics.centeredText(font, "1v1 需要 2 名，2v2 需要 4 名，混战需要 3～8 名已登记 AI", width / 2, 44, 0xA0A0A0);
		if (!UiActionClient.lastMessage().isBlank()) graphics.centeredText(font, UiActionClient.lastMessage(),
			width / 2, 170, UiActionClient.lastSuccess() ? 0xFF9CCC65 : 0xFFEF5350);
	}
}
