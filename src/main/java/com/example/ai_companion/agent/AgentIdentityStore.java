package com.example.ai_companion.agent;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable AI player identities, modes and last known world positions. */
public final class AgentIdentityStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-agent-identities.json");
	private List<StoredAgent> agents = new ArrayList<>();

	public static AgentIdentityStore load() {
		try {
			if (Files.notExists(PATH)) return new AgentIdentityStore();
			AgentIdentityStore loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				AgentIdentityStore.class);
			if (loaded == null || loaded.agents == null) return new AgentIdentityStore();
			List<StoredAgent> valid = new ArrayList<>();
			for (StoredAgent entry : loaded.agents) {
				try { valid.add(entry.normalized()); }
				catch (RuntimeException error) {
					AiCompanionMod.LOGGER.warn("Ignoring one invalid persistent AI identity", error);
				}
			}
			loaded.agents = valid;
			return loaded;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with no persistent AI identities", PATH, error);
			return new AgentIdentityStore();
		}
	}

	public synchronized List<StoredAgent> entries() {
		return List.copyOf(agents);
	}

	public synchronized void replace(List<StoredAgent> next) throws IOException {
		agents = new ArrayList<>(next.stream().map(StoredAgent::normalized).toList());
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	public record StoredAgent(String name, String uuid, String dimension, double x, double y, double z,
			String mode, String targetName, String promptId, String textureValue,
			String textureSignature, long createdAtEpochMillis) {
		public StoredAgent normalized() {
			String safeName = name == null ? "" : name.strip();
			if (!safeName.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid AI name");
			UUID parsed = UUID.fromString(uuid);
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("Invalid AI coordinates");
			}
			AgentMode parsedMode;
			try { parsedMode = AgentMode.valueOf(mode); }
			catch (RuntimeException ignored) { parsedMode = AgentMode.IDLE; }
			return new StoredAgent(safeName, parsed.toString(),
				dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension,
				x, y, z, parsedMode.name(), bounded(targetName, 16), bounded(promptId, 64),
				bounded(textureValue, 16_384), bounded(textureSignature, 4_096),
				Math.max(0, createdAtEpochMillis));
		}

		private static String bounded(String value, int max) {
			String safe = value == null ? "" : value;
			return safe.length() > max ? safe.substring(0, max) : safe;
		}
	}
}
