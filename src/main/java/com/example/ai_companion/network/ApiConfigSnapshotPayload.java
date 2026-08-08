package com.example.ai_companion.network;

import com.example.ai_companion.AiCompanionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative API endpoint/model snapshot. The token itself is never sent to clients. */
public record ApiConfigSnapshotPayload(String apiBase, String model, boolean apiKeyConfigured)
		implements CustomPacketPayload {
	public static final Type<ApiConfigSnapshotPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID, "api_config_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ApiConfigSnapshotPayload> CODEC =
		new StreamCodec<>() {
			@Override public ApiConfigSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
				return new ApiConfigSnapshotPayload(buffer.readUtf(300), buffer.readUtf(100), buffer.readBoolean());
			}
			@Override public void encode(RegistryFriendlyByteBuf buffer, ApiConfigSnapshotPayload payload) {
				buffer.writeUtf(payload.apiBase(), 300);
				buffer.writeUtf(payload.model(), 100);
				buffer.writeBoolean(payload.apiKeyConfigured());
			}
		};

	public ApiConfigSnapshotPayload {
		apiBase = apiBase == null ? "" : apiBase.strip();
		model = model == null ? "" : model.strip();
		if (apiBase.length() > 300 || model.length() > 100) {
			throw new IllegalArgumentException("API configuration snapshot is too large");
		}
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
