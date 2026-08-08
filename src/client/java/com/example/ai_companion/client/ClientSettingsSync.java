package com.example.ai_companion.client;

import com.example.ai_companion.AiCompanionMod;
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

		UiActionClient.send("gameplay.save", Boolean.toString(settings.goldenSpearRushEnabled),
			Integer.toString(settings.durabilityEvery), Integer.toString(settings.hungerEvery),
			Integer.toString(settings.hungerCost), Double.toString(settings.rushStrength),
			Boolean.toString(settings.flexibleEquipmentEnabled));
		UiActionClient.send("spyglass.save", Boolean.toString(settings.spyglassHighlightEnabled),
			Integer.toString(settings.spyglassRadiusChunks), Integer.toString(settings.spyglassHoldSeconds),
			Integer.toString(settings.spyglassDurationSeconds), settings.spyglassTargetCondition,
			Integer.toString(settings.spyglassCooldownSeconds), Integer.toString(settings.spyglassMaxTargets));
	}
}
