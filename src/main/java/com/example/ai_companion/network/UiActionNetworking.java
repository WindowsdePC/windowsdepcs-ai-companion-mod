package com.example.ai_companion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers the command-free client-to-server UI action channel. */
public final class UiActionNetworking {
	private UiActionNetworking() { }

	public static void registerServer(UiActionService service) {
		PayloadTypeRegistry.serverboundPlay().register(UiActionPayload.TYPE, UiActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UiActionPayload.TYPE, (payload, context) ->
			service.handle(context.player(), payload));
	}
}
