package com.example.ai_companion.client;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.exploration.NavigationSnapshot;
import com.example.ai_companion.network.NavigationHudPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Phone-like AR guidance overlay with details, direction cue and boss-bar-style distance progress. */
public final class ExplorerNavigationHud {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(
		AiCompanionMod.MOD_ID, "explorer_navigation_hud");
	private static volatile NavigationSnapshot snapshot = NavigationSnapshot.inactive();
	private static volatile long lastUpdateNanos;

	private ExplorerNavigationHud() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(NavigationHudPayload.TYPE, (payload, context) -> {
			snapshot = payload.snapshot();
			lastUpdateNanos = System.nanoTime();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		HudElementRegistry.attachElementBefore(VanillaHudElements.BOSS_BAR, HUD_ID,
			ExplorerNavigationHud::extractRenderState);
	}

	private static void extractRenderState(GuiGraphicsExtractor graphics,
			net.minecraft.client.DeltaTracker deltaTracker) {
		NavigationSnapshot value = snapshot;
		if (!value.active() || System.nanoTime() - lastUpdateNanos > 5_000_000_000L) return;
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int center = screenWidth / 2;

		// Left and right detail cards mirror a compact phone navigation UI.
		drawCard(graphics, client, 8, 8, 205, 47,
			value.targetType().label() + " · " + compact(value.targetId(), 28),
			"维度 " + compact(value.dimension(), 27),
			String.format(Locale.ROOT, "距离 %.0f 格", value.distance()));
		drawCard(graphics, client, screenWidth - 213, 8, 205, 47,
			String.format(Locale.ROOT, "X %.0f  Y %.0f  Z %.0f", value.x(), value.y(), value.z()),
			String.format(Locale.ROOT, "高度差 %+.0f", value.verticalDifference()),
			"模式 " + value.mode().label());

		int barWidth = Math.min(320, Math.max(160, screenWidth / 3));
		int barLeft = center - barWidth / 2;
		int barTop = 10;
		graphics.fill(barLeft - 2, barTop - 2, barLeft + barWidth + 2, barTop + 10, 0xC0101114);
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 8, 0xFF263238);
		graphics.fill(barLeft, barTop, barLeft + (int) Math.round(barWidth * value.progress()),
			barTop + 8, 0xFF4CAF50);
		String progress = String.format(Locale.ROOT, "%.0f 格 · %.0f%%", value.distance(),
			value.progress() * 100.0);
		graphics.centeredText(client.font, progress, center, barTop + 13, 0xFFFFFFFF);

		String arrow = arrow(value.relativeBearing());
		int arrowY = Math.max(72, client.getWindow().getGuiScaledHeight() / 3);
		graphics.fill(center - 29, arrowY - 15, center + 29, arrowY + 20, 0x7010151B);
		graphics.centeredText(client.font, arrow, center, arrowY - 7, 0xFF80D8FF);
		graphics.centeredText(client.font,
			String.format(Locale.ROOT, "%+.0f°", value.relativeBearing()), center, arrowY + 7,
			0xFFFFFFFF);
	}

	private static void drawCard(GuiGraphicsExtractor graphics, Minecraft client, int x, int y,
			int width, int height, String first, String second, String third) {
		graphics.fill(x, y, x + width, y + height, 0xB010151B);
		graphics.fill(x, y, x + 3, y + height, 0xFF29B6F6);
		graphics.text(client.font, first, x + 8, y + 6, 0xFFFFFFFF);
		graphics.text(client.font, second, x + 8, y + 19, 0xFFB3E5FC);
		graphics.text(client.font, third, x + 8, y + 32, 0xFFFFE082);
	}

	private static String arrow(float bearing) {
		float normalized = ((bearing % 360.0F) + 360.0F) % 360.0F;
		if (normalized < 22.5F || normalized >= 337.5F) return "▲";
		if (normalized < 67.5F) return "↖";
		if (normalized < 112.5F) return "←";
		if (normalized < 157.5F) return "↙";
		if (normalized < 202.5F) return "▼";
		if (normalized < 247.5F) return "↘";
		if (normalized < 292.5F) return "→";
		return "↗";
	}

	private static String compact(String value, int maximum) {
		return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
	}

	private static void clear() {
		snapshot = NavigationSnapshot.inactive();
		lastUpdateNanos = 0L;
	}
}
