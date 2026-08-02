package com.example.ai_companion.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.ai_companion.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Minimal OpenAI Chat Completions compatible adapter. */
public final class OpenAiCompatibleClient {
	private static final Gson GSON = new Gson();
	private final HttpClient http = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	public CompletableFuture<AiDecision> decide(ModConfig config, String system, String observation) {
		if (!config.hasApiKey()) {
			return CompletableFuture.failedFuture(new IllegalStateException("没有 API 令牌"));
		}
		JsonObject body = new JsonObject();
		body.addProperty("model", config.model());
		body.addProperty("max_completion_tokens", config.maxOutputTokens());
		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		messages.add(message("user", observation));
		body.add("messages", messages);

		HttpRequest request = HttpRequest.newBuilder(endpoint(config.apiBase()))
			.timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
			.header("Authorization", "Bearer " + config.effectiveApiKey())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();

		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() / 100 != 2) {
				String safe = response.body().length() > 500 ? response.body().substring(0, 500) : response.body();
				throw new IllegalStateException("AI API HTTP " + response.statusCode() + ": " + safe);
			}
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String text = root.getAsJsonArray("choices").get(0).getAsJsonObject()
				.getAsJsonObject("message").get("content").getAsString().strip();
			if (text.startsWith("```")) {
				text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
			}
			AiDecision parsed = GSON.fromJson(text, AiDecision.class);
			return parsed == null ? new AiDecision("wait", "", 0, 0) : parsed.sanitized();
		});
	}

	private static JsonObject message(String role, String content) {
		JsonObject value = new JsonObject();
		value.addProperty("role", role);
		value.addProperty("content", content);
		return value;
	}

	private static URI endpoint(String base) {
		String normalized = base.strip().replaceAll("/+$", "");
		return URI.create(normalized.endsWith("/chat/completions")
			? normalized : normalized + "/chat/completions");
	}
}
