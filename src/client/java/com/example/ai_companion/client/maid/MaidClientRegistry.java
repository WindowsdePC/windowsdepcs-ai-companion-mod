package com.example.ai_companion.client.maid;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.maid.MaidAppearance;
import com.example.ai_companion.maid.MaidAppearanceRequestPayload;
import com.example.ai_companion.maid.MaidAppearancesPayload;
import com.example.ai_companion.maid.MaidSkins;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only appearance cache; default PNGs are resources and imported PNGs become dynamic textures. */
public final class MaidClientRegistry {
	private static final Map<UUID, MaidAppearance> APPEARANCES = new HashMap<>();
	private static final Map<String, Identifier> CUSTOM_TEXTURES = new HashMap<>();
	private static int refreshTicks;

	private MaidClientRegistry() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(MaidAppearancesPayload.TYPE, (payload, context) -> {
			APPEARANCES.clear();
			payload.appearances().forEach(value -> APPEARANCES.put(value.entityUuid(), value));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> APPEARANCES.clear());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++refreshTicks < 100) return;
			refreshTicks = 0;
			if (client.getConnection() != null && ClientPlayNetworking.canSend(MaidAppearanceRequestPayload.TYPE)) {
				ClientPlayNetworking.send(new MaidAppearanceRequestPayload());
			}
		});
	}

	public static void apply(Avatar avatar, AvatarRenderState state) {
		MaidAppearance appearance = APPEARANCES.get(avatar.getUUID());
		if (appearance == null) return;
		ClientAsset.Texture body = texture(appearance.skinKey(), false);
		ClientAsset.Texture cape = appearance.capeKey().isBlank() ? null : texture(appearance.capeKey(), true);
		if (body == null) return;
		PlayerModelType model = state.skin == null ? PlayerModelType.WIDE : state.skin.model();
		state.skin = PlayerSkin.insecure(body, cape, cape, model);
		if (cape != null) state.showCape = true;
	}

	public static synchronized void registerCustom(String key, Path path) throws Exception {
		try (InputStream input = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(input);
			Identifier id = Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID,
				"dynamic/maid/" + key.substring(key.lastIndexOf('/') + 1));
			Minecraft.getInstance().getTextureManager().register(id,
				new DynamicTexture(() -> "AI maid " + key, image));
			CUSTOM_TEXTURES.put(key, id);
		}
	}

	private static ClientAsset.Texture texture(String key, boolean cape) {
		Identifier custom = CUSTOM_TEXTURES.get(key);
		if (custom != null) return new ClientAsset.ResourceTexture(custom, custom);
		if (!MaidSkins.DEFAULTS.contains(key)) return null;
		Identifier asset = Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID,
			(cape ? "entity/maid/cape/" : "entity/maid/") + key);
		return new ClientAsset.ResourceTexture(asset);
	}
}
