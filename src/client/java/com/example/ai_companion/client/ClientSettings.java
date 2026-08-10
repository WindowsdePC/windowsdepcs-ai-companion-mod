package com.example.ai_companion.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentMode;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-local UI preferences and an editable mirror of server gameplay defaults. */
public final class ClientSettings {
	public static final int SHORTCUT_DEFAULTS_VERSION = 2;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-client-settings.json");
	private static ClientSettings shared;

	public String primaryKey = "V";
	public String secondaryKey = "B";
	public String positionsKey = "F8";
	public int shortcutDefaultsVersion = SHORTCUT_DEFAULTS_VERSION;
	public boolean uiShortcutEnabled = true;
	public boolean agentConsoleShortcutEnabled = true;
	public boolean minigameShortcutEnabled = true;
	public boolean clothNavigationTop = true;
	public String defaultAgentMode = AgentMode.SURVIVAL.name();
	public String apiBase = "https://api.openai.com/v1";
	public String model = "gpt-5-mini";
	public boolean goldenSpearRushEnabled = true;
	public int durabilityEvery = 15;
	public int hungerEvery = 30;
	public int hungerCost = 2;
	public double rushStrength = 0.916;
	public boolean flexibleEquipmentEnabled = false;
	public boolean sprintJumpEnabled = true;
	public boolean spyglassHighlightEnabled = true;
	public int spyglassRadiusChunks = 10;
	public int spyglassHoldSeconds = 1;
	public int spyglassDurationSeconds = 120;
	public String spyglassTargetCondition = "ALL_LIVING";
	public int spyglassCooldownSeconds = 10;
	public int spyglassMaxTargets = 256;
	public boolean screenZoomEnabled = false;
	public String zoomKey = "F6";
	public double zoomFactor = 4.0;
	public double zoomTransitionSeconds = 0.18;
	public boolean clientPerformanceOptimizerEnabled = false;
	public boolean adaptiveExtraRenderDistance = true;
	public int performanceTargetFps = 60;
	public int extraRenderDistance = 96;
	public int minimumExtraRenderDistance = 24;
	public boolean worldNavigatorEnabled = false;
	public String navigatorKey = "F7";
	public String minigameMenuKey = "F9";
	public boolean snakeShortcutEnabled;
	public boolean tetrisShortcutEnabled;
	public boolean minesweeperShortcutEnabled;
	public boolean game2048ShortcutEnabled;
	public boolean rockPaperScissorsShortcutEnabled;
	public String snakeShortcutKey = "KP1";
	public String tetrisShortcutKey = "KP2";
	public String minesweeperShortcutKey = "KP3";
	public String game2048ShortcutKey = "KP4";
	public String rockPaperScissorsShortcutKey = "KP5";
	public String minigameUpKey = "W";
	public String minigameDownKey = "S";
	public String minigameLeftKey = "A";
	public String minigameRightKey = "D";
	public String minigameActionKey = "SPACE";
	public String minigamePauseKey = "P";
	public String minigameRestartKey = "R";
	public String minigameSecondaryKey = "F";
	public boolean minigameUpKeyEnabled = true;
	public boolean minigameDownKeyEnabled = true;
	public boolean minigameLeftKeyEnabled = true;
	public boolean minigameRightKeyEnabled = true;
	public boolean minigameActionKeyEnabled = true;
	public boolean minigamePauseKeyEnabled = true;
	public boolean minigameRestartKeyEnabled = true;
	public boolean minigameSecondaryKeyEnabled = true;
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
				loaded.defaultAgentMode = AgentMode.SURVIVAL.name();
			}
			if (loaded != null && (!root.has("shortcutDefaultsVersion")
					|| loaded.shortcutDefaultsVersion < SHORTCUT_DEFAULTS_VERSION)) {
				// M commonly belongs to minimap mods. Migrate only unchanged legacy defaults so
				// deliberate user choices survive upgrades.
				if ("M".equalsIgnoreCase(loaded.minigameMenuKey)) loaded.minigameMenuKey = "F9";
				if ("C".equalsIgnoreCase(loaded.zoomKey)) loaded.zoomKey = "F6";
				if ("G".equalsIgnoreCase(loaded.navigatorKey)) loaded.navigatorKey = "F7";
				loaded.uiShortcutEnabled = true;
				loaded.agentConsoleShortcutEnabled = true;
				loaded.minigameShortcutEnabled = true;
				loaded.minigameUpKeyEnabled = true;
				loaded.minigameDownKeyEnabled = true;
				loaded.minigameLeftKeyEnabled = true;
				loaded.minigameRightKeyEnabled = true;
				loaded.minigameActionKeyEnabled = true;
				loaded.minigamePauseKeyEnabled = true;
				loaded.minigameRestartKeyEnabled = true;
				loaded.minigameSecondaryKeyEnabled = true;
				loaded.shortcutDefaultsVersion = SHORTCUT_DEFAULTS_VERSION;
			}
			return loaded == null ? new ClientSettings() : loaded.normalized();
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read client UI settings {}; using defaults", PATH, error);
			return new ClientSettings();
		}
	}

	/** One live settings object keeps modified shortcuts effective for every client controller. */
	public static synchronized ClientSettings shared() {
		if (shared == null) shared = load();
		return shared;
	}

	public ClientSettings normalized() {
		primaryKey = normalizeGlobalKey(primaryKey, "V");
		secondaryKey = normalizeGlobalKey(secondaryKey, "B");
		positionsKey = normalizeGlobalKey(positionsKey, "F8");
		try {
			defaultAgentMode = AgentMode.valueOf(defaultAgentMode).name();
		} catch (RuntimeException ignored) {
			defaultAgentMode = AgentMode.SURVIVAL.name();
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
		spyglassMaxTargets = spyglassMaxTargets <= 0 ? 256 : Math.clamp(spyglassMaxTargets, 1, 1024);
		try { spyglassTargetCondition = com.example.ai_companion.spyglass.SpyglassTargetCondition.valueOf(spyglassTargetCondition).name(); }
		catch (RuntimeException ignored) { spyglassTargetCondition = "ALL_LIVING"; }
		shortcutDefaultsVersion = SHORTCUT_DEFAULTS_VERSION;
		zoomKey = normalizeGlobalKey(zoomKey, "F6");
		zoomFactor = Math.clamp(zoomFactor, 1.5, 12.0);
		zoomTransitionSeconds = Math.clamp(zoomTransitionSeconds, 0.0, 1.0);
		performanceTargetFps = Math.clamp(performanceTargetFps, 30, 240);
		extraRenderDistance = Math.clamp(extraRenderDistance, 16, 256);
		minimumExtraRenderDistance = Math.clamp(minimumExtraRenderDistance, 16,
			extraRenderDistance);
		navigatorKey = normalizeGlobalKey(navigatorKey, "F7");
		minigameMenuKey = normalizeGlobalKey(minigameMenuKey, "F9");
		snakeShortcutKey = normalizeGlobalKey(snakeShortcutKey, "KP1");
		tetrisShortcutKey = normalizeGlobalKey(tetrisShortcutKey, "KP2");
		minesweeperShortcutKey = normalizeGlobalKey(minesweeperShortcutKey, "KP3");
		game2048ShortcutKey = normalizeGlobalKey(game2048ShortcutKey, "KP4");
		rockPaperScissorsShortcutKey = normalizeGlobalKey(rockPaperScissorsShortcutKey, "KP5");
		minigameUpKey = normalizeGlobalKey(minigameUpKey, "W");
		minigameDownKey = normalizeGlobalKey(minigameDownKey, "S");
		minigameLeftKey = normalizeGlobalKey(minigameLeftKey, "A");
		minigameRightKey = normalizeGlobalKey(minigameRightKey, "D");
		minigameActionKey = normalizeGameplayKey(minigameActionKey, "SPACE");
		minigamePauseKey = normalizeGlobalKey(minigamePauseKey, "P");
		minigameRestartKey = normalizeGlobalKey(minigameRestartKey, "R");
		minigameSecondaryKey = normalizeGlobalKey(minigameSecondaryKey, "F");
		return this;
	}

	public AgentMode defaultAgentMode() {
		try {
			return AgentMode.valueOf(defaultAgentMode);
		} catch (RuntimeException ignored) {
			return AgentMode.SURVIVAL;
		}
	}

	public void setDefaultAgentMode(AgentMode mode) {
		defaultAgentMode = (mode == null ? AgentMode.SURVIVAL : mode).name();
	}

	public int primaryCode() {
		return globalKeyCode(primaryKey);
	}

	public int secondaryCode() {
		return globalKeyCode(secondaryKey);
	}

	public int zoomCode() {
		return globalKeyCode(zoomKey);
	}

	public int navigatorCode() {
		return globalKeyCode(navigatorKey);
	}
	public int minigameMenuCode() { return globalKeyCode(minigameMenuKey); }
	public int snakeShortcutCode() { return globalKeyCode(snakeShortcutKey); }
	public int tetrisShortcutCode() { return globalKeyCode(tetrisShortcutKey); }
	public int minesweeperShortcutCode() { return globalKeyCode(minesweeperShortcutKey); }
	public int game2048ShortcutCode() { return globalKeyCode(game2048ShortcutKey); }
	public int rockPaperScissorsShortcutCode() { return globalKeyCode(rockPaperScissorsShortcutKey); }
	public int minigameUpCode() { return globalKeyCode(minigameUpKey); }
	public int minigameDownCode() { return globalKeyCode(minigameDownKey); }
	public int minigameLeftCode() { return globalKeyCode(minigameLeftKey); }
	public int minigameRightCode() { return globalKeyCode(minigameRightKey); }
	public int minigameActionCode() { return gameplayKeyCode(minigameActionKey); }
	public int minigamePauseCode() { return globalKeyCode(minigamePauseKey); }
	public int minigameRestartCode() { return globalKeyCode(minigameRestartKey); }
	public int minigameSecondaryCode() { return globalKeyCode(minigameSecondaryKey); }
	public int positionsCode() { return globalKeyCode(positionsKey); }

	public void save() throws IOException {
		normalized();
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	public static String normalizeKey(String value, String fallback) {
		String normalized = value == null ? "" : value.strip().toUpperCase();
		return normalized.matches("[A-Z]") ? normalized : fallback;
	}
	public static String normalizeFunctionKey(String value, String fallback) {
		String normalized = value == null ? "" : value.strip().toUpperCase();
		return normalized.matches("F([1-9]|1[0-2])") ? normalized : fallback;
	}
	public static String normalizeGameplayKey(String value, String fallback) {
		String normalized = value == null ? "" : value.strip().toUpperCase();
		return normalized.equals("SPACE") || normalized.matches("[A-Z0-9]")
			|| normalized.matches("F([1-9]|1[0-2])") || normalized.matches("KP[0-9]")
			? normalized : fallback;
	}
	public static String normalizeGlobalKey(String value, String fallback) {
		String normalized = value == null ? "" : value.strip().toUpperCase();
		return normalized.matches("[A-Z0-9]") || normalized.matches("F([1-9]|1[0-2])")
			|| normalized.matches("KP[0-9]") ? normalized : fallback;
	}

	public static int globalKeyCode(String value) {
		String normalized = normalizeGlobalKey(value, "F9");
		if (normalized.matches("F([1-9]|1[0-2])")) {
			return GLFW.GLFW_KEY_F1 + Integer.parseInt(normalized.substring(1)) - 1;
		}
		if (normalized.matches("KP[0-9]")) {
			return GLFW.GLFW_KEY_KP_0 + Integer.parseInt(normalized.substring(2));
		}
		return normalized.charAt(0);
	}

	public static String keyName(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_SPACE) return "SPACE";
		if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
			return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
		}
		if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
			return "KP" + (keyCode - GLFW.GLFW_KEY_KP_0);
		}
		if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z
				|| keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
			return Character.toString((char) keyCode);
		}
		throw new IllegalArgumentException("仅支持 A-Z、0-9、F1-F12 和数字小键盘 0-9");
	}

	private static int gameplayKeyCode(String key) {
		String normalized = normalizeGameplayKey(key, "SPACE");
		return normalized.equals("SPACE") ? com.mojang.blaze3d.platform.InputConstants.KEY_SPACE
			: globalKeyCode(normalized);
	}
}
