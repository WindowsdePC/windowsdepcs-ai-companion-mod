package com.example.ai_companion.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/** Selects exactly one installed UI library, preferring EclipseUI. */
public enum UiBackend {
	ECLIPSE_UI("EclipseUI 现代化 UI"),
	CLOTH_CONFIG("Cloth Config · 完整九分类管理中心");

	private final String displayName;

	UiBackend(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public Screen createScreen(Screen dashboard, ClientSettings settings) {
		return switch (this) {
			case ECLIPSE_UI -> EclipseUiApiBridge.create(dashboard, settings);
			case CLOTH_CONFIG -> ClothConfigApiBridge.create(dashboard, settings);
		};
	}

	public static UiBackend detectOrThrow() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("eclipseui")) {
			EclipseUiApiBridge.verify();
			return ECLIPSE_UI;
		}
		if (loader.isModLoaded("cloth-config")) {
			ClothConfigApiBridge.verify();
			return CLOTH_CONFIG;
		}
		throw new IllegalStateException("WindowsdePC's AI Companion Mod 需要 EclipseUI 或 Cloth Config；请至少安装其中一个");
	}
}
