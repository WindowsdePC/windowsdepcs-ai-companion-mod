package com.example.ai_companion.navigation;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record NavigationCatalogPayload(boolean enabled, List<NavigationEntry> entries,
		String message) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 4096;
	public static final int MAX_MESSAGE_LENGTH = 240;
	public static final Type<NavigationCatalogPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "navigation_catalog"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavigationCatalogPayload> CODEC =
		new StreamCodec<>() {
			@Override public NavigationCatalogPayload decode(RegistryFriendlyByteBuf buffer) {
				boolean enabled = buffer.readBoolean();
				int count = buffer.readVarInt();
				if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid catalog size");
				List<NavigationEntry> entries = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					entries.add(new NavigationEntry(buffer.readUtf(NavigationEntry.MAX_TYPE_LENGTH),
						buffer.readUtf(NavigationEntry.MAX_ID_LENGTH)));
				}
				return new NavigationCatalogPayload(enabled, entries, buffer.readUtf(MAX_MESSAGE_LENGTH));
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, NavigationCatalogPayload payload) {
				buffer.writeBoolean(payload.enabled());
				buffer.writeVarInt(payload.entries().size());
				for (NavigationEntry entry : payload.entries()) {
					buffer.writeUtf(entry.type(), NavigationEntry.MAX_TYPE_LENGTH);
					buffer.writeUtf(entry.id(), NavigationEntry.MAX_ID_LENGTH);
				}
				buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
			}
		};

	public NavigationCatalogPayload {
		entries = List.copyOf(entries);
		message = message == null ? "" : message;
		if (entries.size() > MAX_ENTRIES || message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("Navigation catalog exceeds protocol bounds");
		}
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
