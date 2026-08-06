package com.example.ai_companion.maid;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MaidAppearancesPayload(List<MaidAppearance> appearances) implements CustomPacketPayload {
	public static final int MAX_MAIDS = 64;
	public static final Type<MaidAppearancesPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "maid_appearances"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MaidAppearancesPayload> CODEC = new StreamCodec<>() {
		@Override public MaidAppearancesPayload decode(RegistryFriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			if (count < 0 || count > MAX_MAIDS) throw new IllegalArgumentException("Invalid maid count");
			List<MaidAppearance> values = new ArrayList<>(count);
			for (int i = 0; i < count; i++) values.add(new MaidAppearance(
				buffer.readUUID(), buffer.readUtf(16), buffer.readUtf(96), buffer.readUtf(96),
				MaidMood.valueOf(buffer.readUtf(16))));
			return new MaidAppearancesPayload(values);
		}
		@Override public void encode(RegistryFriendlyByteBuf buffer, MaidAppearancesPayload payload) {
			buffer.writeVarInt(payload.appearances().size());
			for (MaidAppearance value : payload.appearances()) {
				buffer.writeUUID(value.entityUuid());
				buffer.writeUtf(value.name(), 16);
				buffer.writeUtf(value.skinKey(), 96);
				buffer.writeUtf(value.capeKey(), 96);
				buffer.writeUtf(value.mood().name(), 16);
			}
		}
	};
	public MaidAppearancesPayload { appearances = List.copyOf(appearances); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
