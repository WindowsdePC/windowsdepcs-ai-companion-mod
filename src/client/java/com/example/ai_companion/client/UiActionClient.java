package com.example.ai_companion.client;

import com.example.ai_companion.network.UiActionPayload;
import com.example.ai_companion.network.UiActionResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Arrays;

/** Sends typed UI actions without entering text into the command dispatcher. */
public final class UiActionClient {
	private static String lastMessage = "";
	private static boolean lastSuccess = true;
	private static long revision;
	private UiActionClient() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UiActionResultPayload.TYPE, (payload, context) -> {
			lastMessage = payload.message();
			lastSuccess = payload.success();
			revision++;
		});
	}

	public static void send(String action, String... arguments) {
		if (!ClientPlayNetworking.canSend(UiActionPayload.TYPE)) {
			throw new IllegalStateException("服务器未提供 0.9.7+ UI 操作通道");
		}
		ClientPlayNetworking.send(new UiActionPayload(action, Arrays.asList(arguments)));
	}

	public static String lastMessage() { return lastMessage; }
	public static boolean lastSuccess() { return lastSuccess; }
	public static long revision() { return revision; }
}
