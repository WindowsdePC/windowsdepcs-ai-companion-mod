package com.example.ai_companion.legacy;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Directly-polled 1.20.1 shortcuts. They intentionally stay out of the vanilla Controls list. */
public final class LegacyFabricClient implements ClientModInitializer {
	private boolean comboDown, positionsDown, zoomDown, navigationDown;
	private Integer savedFov;

	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		long window = client.getWindow().getWindow();
		boolean v = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_V);
		boolean b = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_B);
		boolean f8 = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F8);
		boolean c = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_C);
		boolean g = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_G);
		if (v && b && !comboDown && client.screen == null) client.setScreen(new CompanionScreen(false));
		if (f8 && !positionsDown && client.player != null && client.getConnection() != null) client.getConnection().sendCommand("aiplayer positions");
		if (g && !navigationDown && client.screen == null) client.setScreen(new CompanionScreen(true));
		if (c && !zoomDown) { savedFov = client.options.fov().get(); client.options.fov().set(Math.max(30, savedFov / 4)); }
		if (!c && zoomDown && savedFov != null) { client.options.fov().set(savedFov); savedFov = null; }
		comboDown = v && b; positionsDown = f8; zoomDown = c; navigationDown = g;
	}

	private static final class CompanionScreen extends Screen {
		private final boolean navigation;
		private CompanionScreen(boolean navigation) { super(Component.literal(navigation ? "AI 导航" : "WindowsdePC's AI Companion Mod")); this.navigation = navigation; }
		@Override protected void init() {
			int left = width / 2 - 100;
			if (!navigation) {
				addRenderableWidget(Button.builder(Component.literal("查询 AI 位置"), button -> command("aiplayer positions")).bounds(left, height / 2 - 35, 200, 20).build());
				addRenderableWidget(Button.builder(Component.literal("查看功能状态"), button -> command("aiplayer feature status")).bounds(left, height / 2 - 10, 200, 20).build());
			} else addRenderableWidget(Button.builder(Component.literal("查询 AI 位置作为导航目标"), button -> command("aiplayer positions")).bounds(left, height / 2 - 10, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose()).bounds(left, height / 2 + 40, 200, 20).build());
		}
		private void command(String value) { if (minecraft != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand(value); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, navigation ? "G：旧版导航入口" : "V+B 管理 · F8 位置 · 按住 C 缩放 · G 导航", width / 2, 30, 0xFFFFFF);
		}
	}
}
