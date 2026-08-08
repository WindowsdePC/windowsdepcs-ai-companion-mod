package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentPosition;
import com.example.ai_companion.network.AgentPositionRequestPayload;
import com.example.ai_companion.network.AgentPositionsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared server-authoritative AI position cache used by the F8 control console and dashboard. */
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
			if (pressed && !keyHeld) {
				if (client.gui.screen() instanceof AgentConsoleScreen console) console.onClose();
				else if (client.gui.screen() == null && client.getConnection() != null) {
					client.setScreenAndShow(new AgentConsoleScreen(null));
				}
			}
			keyHeld = pressed;
		});
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
	public static boolean waiting() { return waiting; }
	public static String error() { return error; }

	private static void clear() {
		positions = List.of();
		keyHeld = false;
		waiting = false;
		error = "";
	}
}
