package com.example.ai_companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import com.example.ai_companion.client.navigation.NavigationClientController;
import com.example.ai_companion.client.navigation.NavigationHud;
import com.example.ai_companion.client.maid.MaidClientRegistry;
import com.example.ai_companion.client.minigame.MinigameCenterScreen;
import com.example.ai_companion.client.minigame.MinigameProgress;

/** Opens the unified settings screen with a configurable two-key chord. */
public final class AiCompanionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UiActionClient.initialize();
		UiBackend backend = UiBackend.detectOrThrow();
		PromptStore localPrompts = PromptStore.loadClient();
		ClientSettings settings = ClientSettings.load();
		FlexibleEquipmentMode.configureClient(() -> settings.flexibleEquipmentEnabled);
		ScreenZoomController.initialize(settings);
		ClientPerformanceController.initialize(settings);
		AgentPositionHud.initialize(settings);
		NavigationClientController.initialize();
		NavigationHud.initialize();
		MaidClientRegistry.initialize();
		boolean[] chordHeld = {false};
		boolean[] navigatorHeld = {false};
		boolean[] minigameHeld = {false};
		boolean[] sprintJumpLatch = {false};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (settings.sprintJumpEnabled && client.player != null && client.options.keyUp.isDown()
					&& !client.player.isShiftKeyDown()) client.player.setSprinting(true);
			boolean pressed = InputConstants.isKeyDown(client.getWindow(), settings.primaryCode())
				&& InputConstants.isKeyDown(client.getWindow(), settings.secondaryCode());
			if (pressed && !chordHeld[0] && client.getConnection() != null) {
				client.setScreenAndShow(new PromptConfigScreen(localPrompts, settings, backend));
			}
			chordHeld[0] = pressed;
			boolean navigatorPressed = InputConstants.isKeyDown(client.getWindow(), settings.navigatorCode());
			if (settings.worldNavigatorEnabled && navigatorPressed && !navigatorHeld[0]
					&& client.getConnection() != null) {
				NavigationClientController.open(client, client.gui.screen());
			}
			navigatorHeld[0] = navigatorPressed;
			boolean minigamePressed = InputConstants.isKeyDown(client.getWindow(), settings.minigameMenuCode());
			if (minigamePressed && !minigameHeld[0] && client.getConnection() != null
					&& client.gui.screen() == null) {
				client.setScreenAndShow(new MinigameCenterScreen(null, MinigameProgress.load(), settings));
			}
			minigameHeld[0] = minigamePressed;
			if (client.player != null) {
				if (client.player.onGround()) {
					sprintJumpLatch[0] = settings.sprintJumpEnabled && client.player.isSprinting();
				} else if (settings.sprintJumpEnabled && sprintJumpLatch[0]) {
					// Preserve the sprint state during the airborne part of a sprint-jump. The server
					// remains authoritative for movement/collisions; this only avoids a client-side
					// sprint cancellation between take-off and landing.
					client.player.setSprinting(true);
				} else if (!settings.sprintJumpEnabled) {
					sprintJumpLatch[0] = false;
				}
			}
		});
	}

	/** Used by Mod Menu and other client launchers to open the complete, actionable dashboard. */
	public static Screen createConfigScreen(Screen parent) {
		return new PromptConfigScreen(PromptStore.loadClient(), ClientSettings.load(),
			UiBackend.detectOrThrow(), parent);
	}
}
