package com.example.ai_companion.maid;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MaidNetworking {
	private MaidNetworking() { }
	public static void registerServer(MaidManager maids) {
		PayloadTypeRegistry.serverboundPlay().register(MaidAppearanceRequestPayload.TYPE,
			MaidAppearanceRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MaidAppearancesPayload.TYPE, MaidAppearancesPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MaidAppearanceRequestPayload.TYPE, (payload, context) ->
			context.responseSender().sendPacket(new MaidAppearancesPayload(
				maids.appearances().stream().limit(MaidAppearancesPayload.MAX_MAIDS).toList())));
	}
}
