package com.example.ai_companion.voice;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional OpenAI PCM TTS adapter for AI replies played through Simple Voice Chat. */
public final class AiSpeechService {
	private static final Gson GSON = new Gson();
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	private final Set<UUID> active = ConcurrentHashMap.newKeySet();

	public boolean available(ModConfig config) {
		return VoicechatBridge.available() && config.hasApiKey()
			&& config.apiBase().toLowerCase(java.util.Locale.ROOT).contains("openai.com");
	}

	public void speak(Object entity, UUID entityId, String text, ModConfig config) {
		if (!available(config) || text == null || text.isBlank() || !active.add(entityId)) return;
		String input = text.strip();
		if (input.length() > 300) input = input.substring(0, 300);
		JsonObject body = new JsonObject();
		body.addProperty("model", "gpt-4o-mini-tts");
		body.addProperty("voice", "alloy");
		body.addProperty("input", input);
		body.addProperty("response_format", "pcm");
		HttpRequest request = HttpRequest.newBuilder(ttsEndpoint(config.apiBase()))
			.timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
			.header("Authorization", "Bearer " + config.effectiveApiKey())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
		http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
			try {
				if (error != null) throw new IllegalStateException(error);
				if (response.statusCode() / 100 != 2) {
					throw new IllegalStateException("TTS HTTP " + response.statusCode());
				}
				VoicechatBridge.speak(entity, pcm24To48(response.body()));
			} catch (RuntimeException failure) {
				AiCompanionMod.LOGGER.warn("AI voice playback failed for {}: {}", entityId,
					failure.getMessage());
			} finally { active.remove(entityId); }
		});
	}

	private static short[] pcm24To48(byte[] bytes) {
		int samples = bytes.length / 2;
		short[] output = new short[samples * 2];
		for (int index = 0; index < samples; index++) {
			short value = (short) ((bytes[index * 2] & 0xFF) | bytes[index * 2 + 1] << 8);
			output[index * 2] = value;
			output[index * 2 + 1] = value;
		}
		return output;
	}

	private static URI ttsEndpoint(String base) {
		String normalized = base.strip().replaceAll("/+$", "");
		if (normalized.endsWith("/chat/completions")) {
			normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
		}
		return URI.create(normalized + "/audio/speech");
	}
}
