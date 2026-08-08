package com.example.ai_companion.legacy;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu 7.x bridge for the Minecraft 1.20.1 Fabric configuration screen. */
public final class LegacyModMenuApi implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LegacyFabricClient::configScreen;
	}
}
