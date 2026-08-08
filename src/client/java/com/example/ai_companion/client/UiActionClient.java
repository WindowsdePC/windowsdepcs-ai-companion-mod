package com.example.ai_companion.client;

import com.example.ai_companion.network.UiActionPayload;
import com.example.ai_companion.network.UiActionResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Sends typed UI actions without entering text into the command dispatcher. */
public final class UiActionClient {
	private static String lastMessage = "";
	private static boolean lastSuccess = true;
	private static long revision;
	private static final List<Message> messages = new ArrayList<>();
	private static String serverApiBase = "https://api.openai.com/v1";
	private static String serverModel = "gpt-5-mini";
	private static boolean serverTokenConfigured;
	private static long configRevision;
	private UiActionClient() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UiActionResultPayload.TYPE, (payload, context) -> {
			String message = payload.message();
			if (message.startsWith("@config\t")) message = acceptConfigSnapshot(message);
			lastMessage = message;
			lastSuccess = payload.success();
			revision++;
			synchronized (messages) {
				messages.add(new Message(revision, payload.success(), message));
				if (messages.size() > 64) messages.removeFirst();
			}
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
	public static long configRevision() { return configRevision; }
	public static String serverApiBase() { return serverApiBase; }
	public static String serverModel() { return serverModel; }
	public static boolean serverTokenConfigured() { return serverTokenConfigured; }
	public static List<Message> messagesAfter(long afterRevision) {
		synchronized (messages) {
			return messages.stream().filter(value -> value.revision() > afterRevision).toList();
		}
	}

	private static String acceptConfigSnapshot(String encoded) {
		String[] parts = encoded.split("\\t", -1);
		if (parts.length != 4) return "服务器返回了无效的 API 配置快照";
		try {
			serverApiBase = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
			serverModel = new String(Base64.getUrlDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8);
			serverTokenConfigured = Boolean.parseBoolean(parts[3]);
			configRevision++;
			return "已从服务器读取 API 配置；令牌=" + (serverTokenConfigured ? "已配置" : "未配置");
		} catch (IllegalArgumentException error) {
			return "服务器返回了无效的 API 配置快照";
		}
	}

	public record Message(long revision, boolean success, String text) { }
}
