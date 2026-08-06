package com.example.ai_companion.maid;

import com.example.ai_companion.agent.AgentManager;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Owns maid metadata while reusing the existing safe fake-player AI executor. */
public final class MaidManager implements AutoCloseable {
	private final AgentManager agents;
	private final MaidStore store = MaidStore.load();
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
		if (maids.containsKey(key)) throw new IllegalArgumentException("女仆名称已存在: " + name);
		agents.create(owner, name, null, null);
		agents.setPrompt(name, "maid");
		MaidProfile profile = new MaidProfile(name, owner.getUUID(), owner.getGameProfile().name(),
			skinKey, capeKey, MaidMood.HAPPY, false);
		maids.put(key, profile);
		updateNameTag(profile);
		applyHealth(profile, true);
		save();
		return profile;
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
