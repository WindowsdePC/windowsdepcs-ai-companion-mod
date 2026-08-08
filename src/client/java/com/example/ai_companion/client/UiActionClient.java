package com.example.ai_companion.client;

import com.example.ai_companion.network.UiActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Arrays;

/** Sends typed UI actions without entering text into the command dispatcher. */
public final class UiActionClient {
	private UiActionClient() { }

	public static void send(String action, String... arguments) {
		if (!ClientPlayNetworking.canSend(UiActionPayload.TYPE)) {
			throw new IllegalStateException("服务器未提供 0.9.7+ UI 操作通道");
		}
		ClientPlayNetworking.send(new UiActionPayload(action, Arrays.asList(arguments)));
	}
}
