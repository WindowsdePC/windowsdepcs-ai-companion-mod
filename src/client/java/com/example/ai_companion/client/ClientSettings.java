package com.example.ai_companion.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-local UI preferences and an editable mirror of server gameplay defaults. */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-client-settings.json");

	public String primaryKey = "V";
	public String secondaryKey = "B";
	public String defaultAgentMode = AgentMode.HUNTER.name();
	public String apiBase = "https://api.openai.com/v1";
	public String model = "gpt-5-mini";
	public boolean goldenSpearRushEnabled = true;
	public int durabilityEvery = 15;
	public int hungerEvery = 30;
	public int hungerCost = 2;
	public double rushStrength = 0.916;
	public boolean flexibleEquipmentEnabled = false;
	public boolean spyglassHighlightEnabled = true;
	public int spyglassRadiusChunks = 10;
	public int spyglassHoldSeconds = 1;
	public int spyglassDurationSeconds = 120;
	public String spyglassTargetCondition = "ALL_LIVING";
	public int spyglassCooldownSeconds = 10;
	public boolean screenZoomEnabled = false;
	public String zoomKey = "C";
	public double zoomFactor = 4.0;
	public double zoomTransitionSeconds = 0.18;
	public boolean clientPerformanceOptimizerEnabled = false;
	public boolean adaptiveExtraRenderDistance = true;
	public int performanceTargetFps = 60;
	public int extraRenderDistance = 96;
	public int minimumExtraRenderDistance = 24;
	public boolean worldNavigatorEnabled = false;
	public String navigatorKey = "G";
	public boolean mercifulVoidEnabled = false;
	public boolean maximumWorldBorderEnabled = false;

	public static ClientSettings load() {
		try {
			if (Files.notExists(PATH)) {
				ClientSettings created = new ClientSettings();
				created.save();
				return created;
			}
			String json = Files.readString(PATH, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			ClientSettings loaded = GSON.fromJson(root, ClientSettings.class);
			if (loaded != null && !root.has("defaultAgentMode")) {
				loaded.defaultAgentMode = AgentMode.HUNTER.name();
			}
			return loaded == null ? new ClientSettings() : loaded.normalized();
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read client UI settings {}; using defaults", PATH, error);
			return new ClientSettings();
		}
	}

	public ClientSettings normalized() {
		primaryKey = normalizeKey(primaryKey, "V");
		secondaryKey = normalizeKey(secondaryKey, "B");
		try {
			defaultAgentMode = AgentMode.valueOf(defaultAgentMode).name();
		} catch (RuntimeException ignored) {
			defaultAgentMode = AgentMode.HUNTER.name();
		}
		apiBase = apiBase == null || apiBase.isBlank() ? "https://api.openai.com/v1" : apiBase.strip();
		model = model == null || model.isBlank() ? "gpt-5-mini" : model.strip();
		durabilityEvery = Math.clamp(durabilityEvery, 1, 1000);
		hungerEvery = Math.clamp(hungerEvery, 1, 1000);
		hungerCost = Math.clamp(hungerCost, 0, 20);
		rushStrength = Math.clamp(rushStrength, 0.1, 4.0);
		spyglassRadiusChunks = Math.clamp(spyglassRadiusChunks, 1, 32);
		spyglassHoldSeconds = Math.clamp(spyglassHoldSeconds, 1, 10);
		spyglassDurationSeconds = Math.clamp(spyglassDurationSeconds, 1, 600);
		spyglassCooldownSeconds = spyglassCooldownSeconds <= 0 ? 10 : Math.clamp(spyglassCooldownSeconds, 1, 600);
		try { spyglassTargetCondition = com.example.ai_companion.spyglass.SpyglassTargetCondition.valueOf(spyglassTargetCondition).name(); }
		catch (RuntimeException ignored) { spyglassTargetCondition = "ALL_LIVING"; }
		zoomKey = normalizeKey(zoomKey, "C");
		zoomFactor = Math.clamp(zoomFactor, 1.5, 12.0);
		zoomTransitionSeconds = Math.clamp(zoomTransitionSeconds, 0.0, 1.0);
		performanceTargetFps = Math.clamp(performanceTargetFps, 30, 240);
		extraRenderDistance = Math.clamp(extraRenderDistance, 16, 256);
		minimumExtraRenderDistance = Math.clamp(minimumExtraRenderDistance, 16,
			extraRenderDistance);
		navigatorKey = normalizeKey(navigatorKey, "G");
		return this;
	}

	public AgentMode defaultAgentMode() {
		try {
			return AgentMode.valueOf(defaultAgentMode);
		} catch (RuntimeException ignored) {
			return AgentMode.HUNTER;
		}
	}

	public void setDefaultAgentMode(AgentMode mode) {
		defaultAgentMode = (mode == null ? AgentMode.HUNTER : mode).name();
	}

	public int primaryCode() {
		return keyCode(primaryKey);
	}

	public int secondaryCode() {
		return keyCode(secondaryKey);
	}

	public int zoomCode() {
		return keyCode(zoomKey);
	}

	public int navigatorCode() {
		return keyCode(navigatorKey);
	}

	public void save() throws IOException {
		normalized();
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	public static String normalizeKey(String value, String fallback) {
		String normalized = value == null ? "" : value.strip().toUpperCase();
		return normalized.matches("[A-Z]") ? normalized : fallback;
	}

	private static int keyCode(String key) {
		return normalizeKey(key, "B").charAt(0);
	}
}
