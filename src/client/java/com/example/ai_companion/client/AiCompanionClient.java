package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Opens the unified settings screen with a configurable two-key chord. */
public final class AiCompanionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UiBackend backend = UiBackend.detectOrThrow();
		PromptStore localPrompts = PromptStore.loadClient();
		ClientSettings settings = ClientSettings.load();
		FlexibleEquipmentMode.configureClient(() -> settings.flexibleEquipmentEnabled);
		F3BHighlightController.initialize(settings);
		AgentPositionHud.initialize();
		boolean[] chordHeld = {false};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = InputConstants.isKeyDown(client.getWindow(), settings.primaryCode())
				&& InputConstants.isKeyDown(client.getWindow(), settings.secondaryCode());
			if (pressed && !chordHeld[0] && client.getConnection() != null) {
				PromptConfigScreen dashboard = new PromptConfigScreen(localPrompts, settings, backend);
				client.setScreenAndShow(backend.createScreen(dashboard, settings));
			}
			chordHeld[0] = pressed;
		});
	}
}
