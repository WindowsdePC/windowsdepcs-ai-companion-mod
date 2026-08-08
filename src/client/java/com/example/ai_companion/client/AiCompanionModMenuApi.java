package com.example.ai_companion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Makes Mod Menu's Configure button open the complete actionable dashboard. */
public final class AiCompanionModMenuApi implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return AiCompanionClient::createConfigScreen;
	}
}
