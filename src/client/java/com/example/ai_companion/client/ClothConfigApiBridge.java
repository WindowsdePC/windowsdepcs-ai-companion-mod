package com.example.ai_companion.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;

/** Kept isolated so EclipseUI-only installations never resolve Cloth Config classes. */
final class ClothConfigApiBridge {
	private ClothConfigApiBridge() {
	}

	static void verify() {
		if (ConfigBuilder.create() == null) {
			throw new IllegalStateException("Cloth Config 配置界面 API 初始化失败");
		}
	}

	static Screen create(Screen dashboard, ClientSettings settings) {
		// Cloth's generic settings list hid the actual AI actions behind its Done button.
		// Keep Cloth as the supported fallback dependency, but open the complete dashboard directly.
		return dashboard;
	}
}
