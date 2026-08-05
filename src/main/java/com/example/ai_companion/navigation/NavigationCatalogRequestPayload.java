package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NavigationCatalogRequestPayload() implements CustomPacketPayload {
	public static final Type<NavigationCatalogRequestPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_catalog_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationCatalogRequestPayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationCatalogRequestPayload decode(RegistryFriendlyByteBuf buffer) {
				return new NavigationCatalogRequestPayload();
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationCatalogRequestPayload payload) {
			}
		};

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
