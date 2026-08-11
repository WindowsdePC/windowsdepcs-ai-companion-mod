package com.example.ai_companion.maid;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.agent.AgentMode;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Owns maid metadata while reusing the existing safe fake-player AI executor. */
public final class MaidManager implements AutoCloseable {
	private final AgentManager agents;
	private final MaidStore store = MaidStore.load();
	private final MaidInventoryStore inventoryStore = new MaidInventoryStore();
	private final Map<String, MaidProfile> maids = new LinkedHashMap<>();

	public MaidManager(AgentManager agents) {
		this.agents = agents;
		store.profiles().forEach(profile -> maids.put(profile.name().toLowerCase(), profile));
		agents.setPromptDecorator(this::decoratePrompt);
		agents.setActionObserver((name, decision) -> awardWorkExperience(name,
			MaidProgression.workExperienceForAction(decision.action())));
	}

	public synchronized MaidProfile summon(ServerPlayer owner, String name, String skinKey, String capeKey) {
		String key = name.toLowerCase();
		MaidProfile existing = maids.get(key);
		if (existing != null && agents.hasAgent(name)) {
			throw new IllegalArgumentException("女仆名称已存在且实体仍在世界中: " + name);
		}
		if (existing != null && !existing.ownerUuid().equals(owner.getUUID())) {
			throw new IllegalArgumentException("该女仆身份属于其他玩家，不能重新创建");
		}
		if (existing != null && existing.stored()) {
			throw new IllegalArgumentException("该女仆已在背包中，请点击“从背包召唤”");
		}

		boolean created = false;
		try {
			agents.create(owner, name, null, null);
			created = true;
			agents.setMode(name, AgentMode.TEAMMATE, owner.getScoreboardName(),
				owner.level().getServer().getTickCount());
			agents.setPrompt(name, "maid");
			MaidProfile profile = existing == null
				? new MaidProfile(name, owner.getUUID(), owner.getGameProfile().name(),
					skinKey, capeKey, MaidMood.HAPPY, false)
				: existing.withStored(false).withMood(MaidMood.HAPPY);
			maids.put(key, profile);
			updateNameTag(profile);
			applyHealth(profile, true);
			save();
			return profile;
		} catch (RuntimeException error) {
			if (created) agents.remove(name);
			throw error;
		}
	}

	public synchronized void restore(MinecraftServer server) {
		maids.values().stream().filter(profile -> !profile.stored() && agents.hasAgent(profile.name()))
			.forEach(profile -> {
				updateNameTag(profile);
				applyHealth(profile, false);
			});
	}

	public void chat(ServerPlayer sender, String name, String instruction, Consumer<String> result) {
		MaidProfile profile;
		synchronized (this) {
			profile = requireOwned(sender, name);
			setMood(profile, MaidMood.THINKING);
		}
		agents.ask(sender.level().getServer(), name, instruction, message -> {
			synchronized (this) {
				MaidProfile current = require(name);
				setMood(current, message.startsWith("AI 请求失败") ? MaidMood.WORRIED : MaidMood.HAPPY);
			}
			result.accept(message);
		});
	}

	public synchronized MaidProfile profile(String name) { return require(name); }
	public synchronized Collection<MaidProfile> profiles() { return ListCopy.values(maids); }

	public synchronized Collection<MaidAppearance> appearances() {
		return maids.values().stream().filter(profile -> !profile.stored() && agents.hasAgent(profile.name()))
			.map(profile -> new MaidAppearance(agents.managedPlayer(profile.name()).getUUID(), profile.name(),
				profile.skinKey(), profile.capeKey(), profile.mood())).toList();
	}

	public synchronized MaidProfile transfer(ServerPlayer owner, String name, ServerPlayer target) {
		MaidProfile profile = requireOwned(owner, name);
		MaidProfile updated = profile.withOwner(target.getUUID(), target.getGameProfile().name());
		maids.put(updated.name().toLowerCase(), updated);
		updateNameTag(updated);
		save();
		return updated;
	}

	public synchronized MaidProfile collect(ServerPlayer owner, String name) {
		MaidProfile profile = requireOwned(owner, name);
		if (!agents.remove(profile.name())) throw new IllegalStateException("女仆实体当前不存在");
		MaidProfile stored = profile.withStored(true).withMood(MaidMood.CALM);
		maids.put(stored.name().toLowerCase(), stored);
		ItemStack capsule = capsule(stored);
		owner.addItem(capsule);
		if (!capsule.isEmpty()) owner.drop(capsule, false);
		save();
		return stored;
	}

	public synchronized MaidProfile deploy(ServerPlayer owner, String name) {
		MaidProfile profile = require(name);
		if (!profile.ownerUuid().equals(owner.getUUID())) throw new IllegalStateException("你不是该女仆的所有者");
		if (!profile.stored()) throw new IllegalStateException("该女仆已经在世界中");
		int capsuleSlot = findCapsule(owner, profile.name());
		if (capsuleSlot < 0) throw new IllegalStateException("背包中找不到该女仆的收纳物品");
		agents.create(owner, profile.name(), null, null);
		agents.setMode(profile.name(), AgentMode.TEAMMATE, owner.getScoreboardName(),
			owner.level().getServer().getTickCount());
		agents.setPrompt(profile.name(), "maid");
		owner.getInventory().removeItem(capsuleSlot, 1);
		MaidProfile active = profile.withStored(false).withMood(MaidMood.HAPPY);
		maids.put(active.name().toLowerCase(), active);
		updateNameTag(active);
		applyHealth(active, true);
		save();
		return active;
	}

	public boolean voiceChatAvailable() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat");
	}

	public synchronized MaidProfile upgradeWithWorkExperience(ServerPlayer owner, String name) {
		MaidProfile profile = requireOwned(owner, name);
		int cost = MaidProgression.workExperienceCost(profile.level());
		if (profile.workExperience() < cost) {
			throw new IllegalStateException("工作经验不足：需要 " + cost + "，当前 " + profile.workExperience());
		}
		return completeUpgrade(profile, profile.workExperience() - cost);
	}

	public synchronized MaidProfile upgradeWithPlayerExperience(ServerPlayer owner, String name) {
		MaidProfile profile = requireOwned(owner, name);
		int displayedLevels = MaidProgression.playerLevelCost(profile.level());
		int pointCost = MaidProgression.frontLevelPointCost(displayedLevels);
		if (owner.totalExperience < pointCost) {
			throw new IllegalStateException("玩家经验不足：需要前 " + displayedLevels + " 级共 " + pointCost + " 点经验");
		}
		owner.giveExperiencePoints(-pointCost);
		return completeUpgrade(profile, profile.workExperience());
	}

	public synchronized String progressionStatus(String name) {
		MaidProfile profile = require(name);
		if (profile.level() >= MaidProgression.MAX_LEVEL) {
			return "%s · 等级 %d（已满级）· 工作经验 %d · 最大生命 %.0f".formatted(profile.name(),
				profile.level(), profile.workExperience(), MaidProgression.maxHealth(profile.level()));
		}
		int workCost = MaidProgression.workExperienceCost(profile.level());
		int playerLevels = MaidProgression.playerLevelCost(profile.level());
		return "%s · 等级 %d/%d · 工作经验 %d/%d · 玩家升级费用=前%d级(%d点) · 最大生命 %.0f".formatted(
			profile.name(), profile.level(), MaidProgression.MAX_LEVEL, profile.workExperience(), workCost,
			playerLevels, MaidProgression.frontLevelPointCost(playerLevels),
			MaidProgression.maxHealth(profile.level()));
	}

	public synchronized void openInventory(ServerPlayer owner, String name) {
		MaidProfile profile = requireOwned(owner, name);
		MaidInventoryContainer container = new MaidInventoryContainer(owner.getUUID(), profile.level(),
			inventoryStore.load(profile.name(), owner.registryAccess()));
		container.setChangeListener(() -> inventoryStore.save(profile.name(), container.snapshot(),
			owner.registryAccess()));
		int unlocked = container.unlockedStorageSlots();
		Component title = Component.literal("生物背包 · " + profile.name() + " Lv." + profile.level()
			+ " · " + unlocked + "/" + MaidInventoryLayout.STORAGE_SLOTS
			+ "格 · 下方：玩家背包");
		owner.openMenu(new SimpleMenuProvider((containerId, playerInventory, player) ->
			ChestMenu.sixRows(containerId, playerInventory, container), title));
	}

	public synchronized int sortInventory(ServerPlayer owner, String name) {
		MaidProfile profile = requireOwned(owner, name);
		List<ItemStack> stacks = new ArrayList<>(inventoryStore.load(profile.name(), owner.registryAccess()));
		int backpackCount = 0;
		for (int slot = MaidInventoryLayout.FIRST_BACKPACK_SLOT;
				slot < MaidInventoryLayout.TOTAL_SLOTS; slot++) {
			if (BackpackCompatibility.isBackpack(stacks.get(slot))) backpackCount++;
		}
		int unlocked = MaidInventoryLayout.unlockedStorageSlots(profile.level(), backpackCount);
		List<ItemStack> sortable = stacks.subList(0, unlocked).stream().filter(stack -> !stack.isEmpty())
			.map(ItemStack::copy).sorted(Comparator
				.comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
				.thenComparing(stack -> stack.getHoverName().getString())).toList();
		for (int slot = 0; slot < unlocked; slot++) {
			stacks.set(slot, slot < sortable.size() ? sortable.get(slot) : ItemStack.EMPTY);
		}
		inventoryStore.save(profile.name(), stacks, owner.registryAccess());
		return sortable.size();
	}

	public synchronized String inventoryStatus(String name) {
		MaidProfile profile = require(name);
		int base = MaidInventoryLayout.baseUnlockedStorageSlots(profile.level());
		return "%s · 生物背包基础解锁 %d/%d 格 · 外部背包槽 2 · 每个有效背包额外解锁 %d 格".formatted(
			profile.name(), base, MaidInventoryLayout.STORAGE_SLOTS,
			MaidInventoryLayout.STORAGE_SLOTS_PER_BACKPACK);
	}

	private synchronized void awardWorkExperience(String name, int amount) {
		MaidProfile profile = maids.get(name == null ? "" : name.toLowerCase());
		if (profile == null || profile.stored() || amount <= 0) return;
		MaidProfile updated = profile.addWorkExperience(amount);
		maids.put(updated.name().toLowerCase(), updated);
		updateNameTag(updated);
		save();
	}

	private MaidProfile completeUpgrade(MaidProfile profile, int remainingWorkExperience) {
		MaidProfile updated = profile.withProgress(profile.level() + 1, remainingWorkExperience)
			.withMood(MaidMood.HAPPY);
		maids.put(updated.name().toLowerCase(), updated);
		applyHealth(updated, true);
		updateNameTag(updated);
		save();
		return updated;
	}

	private void applyHealth(MaidProfile profile, boolean healToMaximum) {
		if (profile.stored() || !agents.hasAgent(profile.name())) return;
		var player = agents.managedPlayer(profile.name());
		var attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) return;
		double maximum = MaidProgression.maxHealth(profile.level());
		attribute.setBaseValue(maximum);
		if (healToMaximum) player.setHealth((float) maximum);
		else if (player.getHealth() > maximum) player.setHealth((float) maximum);
	}

	private String decoratePrompt(String agentName, String prompt) {
		MaidProfile profile;
		synchronized (this) { profile = maids.get(agentName.toLowerCase()); }
		if (profile == null) return prompt;
		return prompt.replace("{Maid:Name}", profile.name())
			.replace("{Player:Name}", profile.ownerName())
			.replace("{Player:UUID}", profile.ownerUuid().toString())
			.replace("{Maid:Mood}", profile.mood().displayName());
	}

	private MaidProfile requireOwned(ServerPlayer player, String name) {
		MaidProfile profile = require(name);
		if (!profile.ownerUuid().equals(player.getUUID())) throw new IllegalStateException("你不是该女仆的所有者");
		if (profile.stored()) throw new IllegalStateException("该女仆当前在背包中");
		return profile;
	}

	private static ItemStack capsule(MaidProfile profile) {
		ItemStack stack = new ItemStack(Items.ENDER_EYE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("收纳的 AI 女仆 · " + profile.name()));
		CompoundTag tag = new CompoundTag();
		tag.putString("ai_companion_maid", profile.name());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static int findCapsule(ServerPlayer owner, String name) {
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data != null && name.equals(data.copyTag().getStringOr("ai_companion_maid", ""))) return slot;
		}
		return -1;
	}

	private MaidProfile require(String name) {
		MaidProfile profile = maids.get(name == null ? "" : name.toLowerCase());
		if (profile == null) throw new IllegalArgumentException("找不到 AI 女仆: " + name);
		return profile;
	}

	private void setMood(MaidProfile profile, MaidMood mood) {
		MaidProfile updated = profile.withMood(mood);
		maids.put(updated.name().toLowerCase(), updated);
		updateNameTag(updated);
		save();
	}

	private void updateNameTag(MaidProfile profile) {
		if (!agents.hasAgent(profile.name())) return;
		agents.managedPlayer(profile.name()).setCustomName(Component.literal(
			profile.name() + "  Lv." + profile.level() + "  💬 心情：" + profile.mood().displayName()));
		agents.managedPlayer(profile.name()).setCustomNameVisible(true);
	}

	private void save() { store.replace(maids.values().stream().toList()); }

	@Override public synchronized void close() { save(); }

	/** Avoids exposing a live values view outside synchronization. */
	private static final class ListCopy {
		static <K, V> Collection<V> values(Map<K, V> source) { return source.values().stream().toList(); }
	}
}
