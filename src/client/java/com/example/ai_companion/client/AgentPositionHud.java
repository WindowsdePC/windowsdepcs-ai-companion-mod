package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.agent.AgentPosition;
import com.example.ai_companion.network.AgentPositionRequestPayload;
import com.example.ai_companion.network.AgentPositionsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Opens the F8 AI console and keeps its position snapshot server-authoritative. */
public final class AgentPositionHud {
	private static List<AgentPosition> positions = List.of();
	private static boolean keyHeld;
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
			if (pressed && !keyHeld && client.getConnection() != null) {
				Screen current = client.gui.screen();
				if (current instanceof AgentConsoleScreen console) console.onClose();
				else client.setScreenAndShow(new AgentConsoleScreen(current, settings));
			}
			keyHeld = pressed;
		});
	}

	public static void requestRefresh(Minecraft client) {
		positions = List.of();
		waiting = true;
		error = "";
		revision++;
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

	private static void clear() {
		positions = List.of();
		keyHeld = false;
		waiting = false;
		error = "";
		revision++;
	}
}
