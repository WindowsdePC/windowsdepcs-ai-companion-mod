package com.example.ai_companion.client;

import com.example.ai_companion.network.UiActionPayload;
import com.example.ai_companion.network.UiActionResultPayload;
import com.example.ai_companion.network.ApiConfigRequestPayload;
import com.example.ai_companion.network.ApiConfigSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Sends typed UI actions without entering text into the command dispatcher. */
public final class UiActionClient {
	private static String lastMessage = "";
	private static boolean lastSuccess = true;
	private static long revision;
	private static final List<Message> messages = new ArrayList<>();
	private static ApiConfigSnapshotPayload apiConfig;
	private static long apiConfigRevision;

	public record Message(boolean success, String text) { }
	private UiActionClient() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UiActionResultPayload.TYPE, (payload, context) -> {
			lastMessage = payload.message();
			lastSuccess = payload.success();
			revision++;
			append(payload.success(), payload.message());
		});
		ClientPlayNetworking.registerGlobalReceiver(ApiConfigSnapshotPayload.TYPE, (payload, context) -> {
			apiConfig = payload;
			apiConfigRevision++;
		});
	}

	public static void send(String action, String... arguments) {
		if (!ClientPlayNetworking.canSend(UiActionPayload.TYPE)) {
			throw new IllegalStateException("服务器未提供 0.9.7+ UI 操作通道");
		}
		ClientPlayNetworking.send(new UiActionPayload(action, Arrays.asList(arguments)));
	}

	public static void requestApiConfig() {
		if (ClientPlayNetworking.canSend(ApiConfigRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new ApiConfigRequestPayload());
		}
	}

	public static void note(String text) { append(true, text); }
	private static void append(boolean success, String text) {
		String safe = text == null ? "" : text.strip();
		if (safe.isEmpty()) return;
		messages.add(new Message(success, safe));
		if (messages.size() > 100) messages.removeFirst();
	}

	public static List<Message> messages() { return List.copyOf(messages); }
	public static ApiConfigSnapshotPayload apiConfig() { return apiConfig; }
	public static long apiConfigRevision() { return apiConfigRevision; }

	public static String lastMessage() { return lastMessage; }
	public static boolean lastSuccess() { return lastSuccess; }
	public static long revision() { return revision; }
}
