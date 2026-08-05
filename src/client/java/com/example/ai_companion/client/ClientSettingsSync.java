package com.example.ai_companion.client;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;

/** Saves client options and mirrors gameplay values to an attached server when permitted. */
final class ClientSettingsSync {
	private ClientSettingsSync() {
	}

	static void save(ClientSettings settings) {
		settings.primaryKey = ClientSettings.normalizeKey(settings.primaryKey, "V");
		settings.secondaryKey = ClientSettings.normalizeKey(settings.secondaryKey, "B");
		settings.zoomKey = ClientSettings.normalizeKey(settings.zoomKey, "C");
		settings.normalized();
		try {
			settings.save();
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save client UI settings", error);
			throw new IllegalStateException("客户端设置保存失败", error);
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() == null) return;
		minecraft.getConnection().sendCommand("aiplayer feature enabled " + settings.goldenSpearRushEnabled);
		minecraft.getConnection().sendCommand("aiplayer feature durability-every " + settings.durabilityEvery);
		minecraft.getConnection().sendCommand("aiplayer feature hunger-every " + settings.hungerEvery);
		minecraft.getConnection().sendCommand("aiplayer feature hunger-cost " + settings.hungerCost);
		minecraft.getConnection().sendCommand("aiplayer feature strength " + settings.rushStrength);
		minecraft.getConnection().sendCommand("aiplayer feature flexible-equipment "
			+ settings.flexibleEquipmentEnabled);
		minecraft.getConnection().sendCommand("navigator feature navigation "
			+ settings.explorerNavigatorEnabled);
		minecraft.getConnection().sendCommand("navigator feature world-limits "
			+ settings.worldLimitsRemoved);
		minecraft.getConnection().sendCommand("navigator feature merciful-void "
			+ settings.mercifulVoidEnabled);
		minecraft.getConnection().sendCommand("navigator mode "
			+ settings.explorerNavigationMode().id());
		if (settings.explorerTargetType() == com.example.ai_companion.exploration.NavigationTargetType.BORDERLANDS) {
			minecraft.getConnection().sendCommand("navigator target borderlands");
		} else {
			minecraft.getConnection().sendCommand("navigator target "
				+ settings.explorerTargetType().id() + " " + settings.explorerTargetId);
		}
	}
}
