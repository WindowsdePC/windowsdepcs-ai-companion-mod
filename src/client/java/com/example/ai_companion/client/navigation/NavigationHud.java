package com.example.ai_companion.client.navigation;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.navigation.NavigationMath;
import com.example.ai_companion.navigation.NavigationTargetPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** AR-like direction ribbon and boss-bar-style remaining distance display. */
public final class NavigationHud {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(AiCompanionMod.MOD_ID,
		"world_navigation_hud");
	private static NavigationTargetPayload target;

	private NavigationHud() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, NavigationHud::render);
	}

	public static void setTarget(NavigationTargetPayload next) { target = next; }
	public static void clear() { target = null; }

	private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (target == null || client.player == null || client.level == null) return;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		String currentDimension = client.level.dimension().identifier().toString();
		boolean sameDimension = currentDimension.equals(target.dimension());
		double distance = sameDimension ? NavigationMath.horizontalDistance(client.player.getX(),
			client.player.getZ(), target.x(), target.z()) : -1;
		int barWidth = Math.min(220, screenWidth - 80);
		int left = (screenWidth - barWidth) / 2;
		int top = 8;
		String navigationType = switch (target.targetType()) {
			case "biome" -> "群系";
			case "structure" -> "结构";
			case "dimension" -> "维度";
			default -> "目标";
		};
		graphics.fill(4, 4, Math.min(screenWidth - 4, 260), 28, 0xB010151B);
		graphics.text(client.font, "正在导航 · " + navigationType, 9, 8, 0xFF90CAF9);
		graphics.text(client.font, target.id(), 9, 18, 0xFFFFD54F);
		graphics.fill(left - 4, top - 3, left + barWidth + 4, top + 34, 0xB010151B);
		String targetLabel = sameDimension ? navigationType + " · " + target.id()
			: "目标维度 · " + target.dimension();
		graphics.centeredText(client.font, targetLabel, screenWidth / 2, top, 0xFFFFD54F);
		graphics.fill(left, top + 12, left + barWidth, top + 19, 0xFF401010);
		int progressWidth = sameDimension ? (int) Math.round(barWidth * NavigationMath.progress(distance,
			target.startingDistance())) : 0;
		graphics.fill(left, top + 12, left + progressWidth, top + 19, 0xFFD32F2F);
		String distanceText = sameDimension ? Math.round(distance) + " 格" : "请先进入目标维度";
		graphics.centeredText(client.font, distanceText, screenWidth / 2, top + 23, 0xFFFFFFFF);
		if (sameDimension) {
			double bearing = NavigationMath.relativeBearing(client.player.getX(), client.player.getZ(),
				client.player.getYRot(), target.x(), target.z());
			String arrow = arrow(bearing);
			graphics.centeredText(client.font, arrow, screenWidth / 2, top + 38, 0xFF80D8FF);
			if (distance > 12 && NavigationMath.offCourse(bearing)) {
				int warningY = Math.max(70, client.getWindow().getGuiScaledHeight() / 3);
				graphics.centeredText(client.font, "您已偏航", screenWidth / 2, warningY, 0xFFFF3030);
				String correct = "正确方向：" + NavigationMath.cardinalDirection(client.player.getX(),
					client.player.getZ(), target.x(), target.z()) + " · " + arrow;
				graphics.centeredText(client.font, correct, screenWidth / 2, warningY + 14, 0xFFFFFFFF);
			}
			if (distance <= 8) graphics.centeredText(client.font, "已到达目标附近", screenWidth / 2,
				top + 50, 0xFF69F0AE);
		}
	}

	private static String arrow(double bearing) {
		if (bearing < -157.5 || bearing >= 157.5) return "▼ 后方";
		if (bearing < -112.5) return "↙ 左后方";
		if (bearing < -67.5) return "← 左转";
		if (bearing < -22.5) return "↖ 左前方";
		if (bearing < 22.5) return "▲ 直行";
		if (bearing < 67.5) return "↗ 右前方";
		if (bearing < 112.5) return "→ 右转";
		return "↘ 右后方";
	}
}
