package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.example.ai_companion.client.navigation.NavigationClientController;
import com.example.ai_companion.client.navigation.NavigationHud;
import com.example.ai_companion.client.maid.MaidClientRegistry;

/** Opens the unified settings screen with a configurable two-key chord. */
public final class AiCompanionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UiBackend backend = UiBackend.detectOrThrow();
		PromptStore localPrompts = PromptStore.loadClient();
		ClientSettings settings = ClientSettings.load();
		FlexibleEquipmentMode.configureClient(() -> settings.flexibleEquipmentEnabled);
		ScreenZoomController.initialize(settings);
		ClientPerformanceController.initialize(settings);
		AgentPositionHud.initialize();
		NavigationClientController.initialize();
		NavigationHud.initialize();
		MaidClientRegistry.initialize();
		boolean[] chordHeld = {false};
		boolean[] navigatorHeld = {false};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = InputConstants.isKeyDown(client.getWindow(), settings.primaryCode())
				&& InputConstants.isKeyDown(client.getWindow(), settings.secondaryCode());
			if (pressed && !chordHeld[0] && client.getConnection() != null) {
				PromptConfigScreen dashboard = new PromptConfigScreen(localPrompts, settings, backend);
				client.setScreenAndShow(backend.createScreen(dashboard, settings));
			}
			chordHeld[0] = pressed;
			boolean navigatorPressed = InputConstants.isKeyDown(client.getWindow(), settings.navigatorCode());
			if (settings.worldNavigatorEnabled && navigatorPressed && !navigatorHeld[0]
					&& client.getConnection() != null) {
				NavigationClientController.open(client, client.gui.screen());
			}
			navigatorHeld[0] = navigatorPressed;
		});
	}
}
