package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentPosition;
import com.example.ai_companion.network.AgentPositionRequestPayload;
import com.example.ai_companion.network.AgentPositionsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** F8 hold-to-view HUD backed by a fresh server-authoritative position snapshot per press. */
public final class AgentPositionHud {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(
		AiCompanionMod.MOD_ID, "agent_position_hud");
	private static final int PANEL_WIDTH = 520;
	private static final int LINE_HEIGHT = 13;
	private static final int MAX_VISIBLE_ROWS = 16;

	private static List<AgentPosition> positions = List.of();
	private static boolean keyHeld;
	private static boolean visible;
	private static boolean waiting;
	private static String error = "";
	private static long revision;

	private AgentPositionHud() {
	}

	public static void initialize(ClientSettings settings) {
		ClientPlayNetworking.registerGlobalReceiver(AgentPositionsPayload.TYPE, (payload, context) -> {
			positions = payload.positions();
			waiting = false;
			error = "";
			revision++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = InputConstants.isKeyDown(client.getWindow(), settings.positionsCode());
			if (pressed && !keyHeld) {
				visible = true;
				requestRefresh(client);
			} else if (!pressed) {
				visible = false;
			}
			keyHeld = pressed;
		});
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT, HUD_ID, AgentPositionHud::extractRenderState);
	}

	public static void requestRefresh(Minecraft client) {
		positions = List.of();
		waiting = true;
		error = "";
		if (client.getConnection() == null || !ClientPlayNetworking.canSend(AgentPositionRequestPayload.TYPE)) {
			waiting = false;
			error = Component.translatable("hud.ai_companion.positions.unsupported").getString();
			return;
		}
		ClientPlayNetworking.send(new AgentPositionRequestPayload());
	}

	public static List<AgentPosition> snapshot() {
		return List.copyOf(positions);
	}

	public static long revision() {
		return revision;
	}

	private static void extractRenderState(GuiGraphicsExtractor graphics,
			net.minecraft.client.DeltaTracker deltaTracker) {
		if (!visible) return;
		Minecraft client = Minecraft.getInstance();

		int rows = waiting || !error.isBlank() || positions.isEmpty()
			? 1 : Math.min(positions.size(), MAX_VISIBLE_ROWS);
		int panelHeight = 38 + rows * LINE_HEIGHT + (positions.size() > MAX_VISIBLE_ROWS ? LINE_HEIGHT : 0);
		int actualWidth = Math.min(PANEL_WIDTH, client.getWindow().getGuiScaledWidth() - 24);
		int left = (client.getWindow().getGuiScaledWidth() - actualWidth) / 2;
		int top = 18;
		graphics.fill(left, top, left + actualWidth, top + panelHeight, 0xD010151B);
		graphics.fill(left, top, left + actualWidth, top + 2, 0xFF42A5F5);
		graphics.text(client.font, Component.translatable("hud.ai_companion.positions.title"),
			left + 7, top + 7, 0xFFFFFFFF);
		graphics.text(client.font, "AI名称", left + 12, top + 22, 0xFFB0BEC5);
		graphics.text(client.font, "当前维度 / 位置", left + Math.min(150, actualWidth / 3), top + 22, 0xFFB0BEC5);

		int y = top + 36;
		if (waiting) {
			graphics.text(client.font, Component.translatable("hud.ai_companion.positions.loading"),
				left + 7, y, 0xFFB0BEC5);
			return;
		}
		if (!error.isBlank()) {
			graphics.text(client.font, error, left + 7, y, 0xFFFF8A80);
			return;
		}
		if (positions.isEmpty()) {
			graphics.text(client.font, Component.translatable("hud.ai_companion.positions.empty"),
				left + 7, y, 0xFFB0BEC5);
			return;
		}
		for (int index = 0; index < Math.min(positions.size(), MAX_VISIBLE_ROWS); index++) {
			graphics.text(client.font, positions.get(index).displayText(), left + 12, y,
				index % 2 == 0 ? 0xFFE3F2FD : 0xFFB3E5FC);
			y += LINE_HEIGHT;
		}
		if (positions.size() > MAX_VISIBLE_ROWS) {
			graphics.text(client.font, Component.translatable("hud.ai_companion.positions.more",
				positions.size() - MAX_VISIBLE_ROWS), left + 7, y, 0xFFFFD54F);
		}
	}

	private static void clear() {
		positions = List.of();
		keyHeld = false;
		visible = false;
		waiting = false;
		error = "";
	}
}
