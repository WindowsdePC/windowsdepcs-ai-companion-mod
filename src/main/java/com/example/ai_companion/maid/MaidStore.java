package com.example.ai_companion.maid;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JSON persistence kept separate from generic AI identities for safe migration. */
public final class MaidStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-maids.json");
	private List<Entry> maids = new ArrayList<>();

	public static MaidStore load() {
		try {
			if (Files.notExists(PATH)) return new MaidStore();
			MaidStore value = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), MaidStore.class);
			if (value == null || value.maids == null) return new MaidStore();
			return value;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read maid store", error);
			return new MaidStore();
		}
	}

	public synchronized List<MaidProfile> profiles() {
		List<MaidProfile> result = new ArrayList<>();
		for (Entry entry : maids) {
			try { result.add(entry.toProfile()); }
			catch (RuntimeException error) { AiCompanionMod.LOGGER.warn("Ignoring invalid maid entry", error); }
		}
		return List.copyOf(result);
	}

	public synchronized void replace(List<MaidProfile> profiles) {
		maids = profiles.stream().map(Entry::from).toList();
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (Exception error) {
			throw new IllegalStateException("无法保存女仆数据", error);
		}
	}

	private record Entry(String name, String ownerUuid, String ownerName, String skinKey,
			String capeKey, String mood, boolean stored, int level, int workExperience) {
		MaidProfile toProfile() {
			MaidMood parsed;
			try { parsed = MaidMood.valueOf(mood); } catch (RuntimeException ignored) { parsed = MaidMood.CALM; }
			return new MaidProfile(name, UUID.fromString(ownerUuid), ownerName, skinKey, capeKey, parsed,
				stored, level, workExperience);
		}
		static Entry from(MaidProfile profile) {
			return new Entry(profile.name(), profile.ownerUuid().toString(), profile.ownerName(),
				profile.skinKey(), profile.capeKey(), profile.mood().name(), profile.stored(),
				profile.level(), profile.workExperience());
		}
	}
}
