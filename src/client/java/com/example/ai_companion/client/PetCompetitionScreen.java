package com.example.ai_companion.client;

import com.example.ai_companion.pet.PetCompetitionMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Complete pet creation, training and competition popup. */
public final class PetCompetitionScreen extends Screen {
	private final Screen parent;
	private String name = "Swift";
	private String speed = "80";
	private String strength = "40";
	private String endurance = "60";
	private String opponent = "Tank";

	public PetCompetitionScreen(Screen parent) {
		super(Component.literal("AI 宠物竞技"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panel = Math.min(660, width - 40);
		int left = (width - panel) / 2;
		int quarter = (panel - 24) / 4;
		textBox(left, 62, quarter, name, 24, value -> name = value);
		textBox(left + quarter + 8, 62, quarter, speed, 3, value -> speed = value);
		textBox(left + (quarter + 8) * 2, 62, quarter, strength, 3, value -> strength = value);
		textBox(left + (quarter + 8) * 3, 62, quarter, endurance, 3, value -> endurance = value);
		addRenderableWidget(Button.builder(Component.literal("创建宠物"), b -> UiActionClient.send("pet.create",
			name, speed, strength, endurance)).bounds(left, 96, panel / 3 - 6, 22).build());
		addRenderableWidget(Button.builder(Component.literal("我的宠物"), b -> UiActionClient.send("pet.list"))
			.bounds(left + panel / 3 + 3, 96, panel / 3 - 6, 22).build());
		addRenderableWidget(Button.builder(Component.literal("排行榜"), b -> UiActionClient.send("pet.leaderboard"))
			.bounds(left + (panel / 3 + 3) * 2, 96, panel / 3 - 6, 22).build());
		textBox(left, 136, panel / 2 - 5, opponent, 24, value -> opponent = value);
		addRenderableWidget(Button.builder(Component.literal("训练速度"), b -> train("speed"))
			.bounds(left + panel / 2 + 5, 136, (panel / 2 - 15) / 3, 22).build());
		addRenderableWidget(Button.builder(Component.literal("训练力量"), b -> train("strength"))
			.bounds(left + panel / 2 + 10 + (panel / 2 - 15) / 3, 136, (panel / 2 - 15) / 3, 22).build());
		addRenderableWidget(Button.builder(Component.literal("训练耐力"), b -> train("endurance"))
			.bounds(left + panel / 2 + 15 + (panel / 2 - 15) / 3 * 2, 136, (panel / 2 - 15) / 3, 22).build());
		addRenderableWidget(Button.builder(Component.literal("与对手竞速"), b -> compete(PetCompetitionMode.RACE))
			.bounds(left, 174, panel / 2 - 5, 22).build());
		addRenderableWidget(Button.builder(Component.literal("与对手战斗"), b -> compete(PetCompetitionMode.BATTLE))
			.bounds(left + panel / 2 + 5, 174, panel / 2 - 5, 22).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + panel / 2 - 55, 216, 110, 20).build());
	}

	private EditBox textBox(int x, int y, int width, String value, int max,
			java.util.function.Consumer<String> responder) {
		EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 22, Component.literal("字段")));
		box.setMaxLength(max);
		box.setValue(value);
		box.setResponder(responder);
		return box;
	}

	private void train(String attribute) { UiActionClient.send("pet.train", name, attribute); }
	private void compete(PetCompetitionMode mode) {
		UiActionClient.send("pet.compete", mode.name(), name, opponent);
	}

	@Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 20, 0xFFFFFF);
		graphics.centeredText(font, "宠物名 / 速度 / 力量 / 耐力（单项 10～100，总和不超过 180）", width / 2, 42, 0xA0A0A0);
		graphics.centeredText(font, "下方左侧填写对手名；训练始终作用于上方宠物名", width / 2, 122, 0xA0A0A0);
		if (!UiActionClient.lastMessage().isBlank()) graphics.centeredText(font, UiActionClient.lastMessage(),
			width / 2, 202, UiActionClient.lastSuccess() ? 0xFF9CCC65 : 0xFFEF5350);
	}
}
