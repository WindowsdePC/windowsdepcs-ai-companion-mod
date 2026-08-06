package com.example.ai_companion.maid;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persists complete ItemStack components without depending on another backpack mod's API. */
final class MaidInventoryStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-maid-inventories.json");
	private final JsonObject inventories;

	MaidInventoryStore() {
		JsonObject loaded = new JsonObject();
		try {
			if (Files.exists(PATH)) {
				JsonElement parsed = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), JsonElement.class);
				if (parsed != null && parsed.isJsonObject()) loaded = parsed.getAsJsonObject();
			}
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read maid inventory store", error);
		}
		inventories = loaded;
	}

	synchronized List<ItemStack> load(String name, HolderLookup.Provider registries) {
		List<ItemStack> result = emptyInventory();
		JsonElement value = inventories.get(key(name));
		if (value == null || !value.isJsonArray()) return result;
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		for (JsonElement element : value.getAsJsonArray()) {
			try {
				JsonObject entry = element.getAsJsonObject();
				int slot = entry.get("slot").getAsInt();
				if (slot < 0 || slot >= MaidInventoryLayout.TOTAL_SLOTS) continue;
				ItemStack stack = ItemStack.CODEC.parse(ops, entry.get("stack")).getOrThrow();
				result.set(slot, stack);
			} catch (RuntimeException error) {
				AiCompanionMod.LOGGER.warn("Ignoring damaged inventory entry for {}", name, error);
			}
		}
		return result;
	}

	synchronized void save(String name, List<ItemStack> stacks, HolderLookup.Provider registries) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		JsonArray entries = new JsonArray();
		for (int slot = 0; slot < Math.min(stacks.size(), MaidInventoryLayout.TOTAL_SLOTS); slot++) {
			ItemStack stack = stacks.get(slot);
			if (stack == null || stack.isEmpty()) continue;
			try {
				JsonObject entry = new JsonObject();
				entry.addProperty("slot", slot);
				entry.add("stack", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
				entries.add(entry);
			} catch (RuntimeException error) {
				AiCompanionMod.LOGGER.warn("Cannot encode maid inventory slot {} for {}", slot, name, error);
			}
		}
		inventories.add(key(name), entries);
		flush();
	}

	private void flush() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(inventories) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (Exception error) {
			throw new IllegalStateException("无法保存女仆背包", error);
		}
	}

	private static List<ItemStack> emptyInventory() {
		List<ItemStack> stacks = new ArrayList<>(MaidInventoryLayout.TOTAL_SLOTS);
		for (int slot = 0; slot < MaidInventoryLayout.TOTAL_SLOTS; slot++) stacks.add(ItemStack.EMPTY);
		return stacks;
	}

	private static String key(String name) { return name == null ? "" : name.toLowerCase(); }
}
