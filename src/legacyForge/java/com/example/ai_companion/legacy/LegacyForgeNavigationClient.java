package com.example.ai_companion.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Forge 47 client screen and HUD for the old 1.20.1 navigation protocol. */
final class LegacyForgeNavigationClient {
	private static List<LegacyForgeNavigation.Entry> entries = List.of();
	private static String message = "";
	private static int revision;
	private static Target target;

	private LegacyForgeNavigationClient() { }
	static void acceptCatalog(List<LegacyForgeNavigation.Entry> next, String status) { entries = List.copyOf(next); message = status; revision++; }
	static void acceptTarget(boolean success, String type, String id, String dimension, double x, double y, double z, double starting, String status) { message = status; if (success) target = new Target(type, id, dimension, x, y, z, starting); revision++; }
	static void acceptState(boolean active, String status) { message = status; if (!active) target = null; revision++; }
	static void clear() { entries = List.of(); message = ""; target = null; revision++; }

	static void open(Minecraft client, Screen parent) { LegacyForgeNavigation.requestCatalog(); message = "正在读取服务器注册表……"; revision++; client.setScreen(new NavigatorScreen(parent)); }

	static void renderHud(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance(); if (target == null || client.player == null || client.level == null) return;
		int screenWidth = client.getWindow().getGuiScaledWidth(), screenHeight = client.getWindow().getGuiScaledHeight();
		boolean sameDimension = client.level.dimension().location().toString().equals(target.dimension);
		double distance = sameDimension ? Math.hypot(target.x - client.player.getX(), target.z - client.player.getZ()) : -1;
		String type = label(target.type); graphics.fill(4, 4, Math.min(screenWidth - 4, 260), 28, 0xB010151B);
		graphics.drawString(client.font, "正在导航 · " + type, 9, 8, 0x90CAF9); graphics.drawString(client.font, target.id, 9, 18, 0xFFD54F);
		int barWidth = Math.min(220, screenWidth - 80), left = (screenWidth - barWidth) / 2, top = 8;
		graphics.fill(left - 4, top - 3, left + barWidth + 4, top + 34, 0xB010151B);
		graphics.drawCenteredString(client.font, sameDimension ? type + " · " + target.id : "目标维度 · " + target.dimension, screenWidth / 2, top, 0xFFD54F);
		graphics.fill(left, top + 12, left + barWidth, top + 19, 0xFF401010); int progress = sameDimension && target.starting > 0 ? (int) Math.round(barWidth * Math.max(0, Math.min(1, 1 - distance / target.starting))) : 0;
		graphics.fill(left, top + 12, left + progress, top + 19, 0xFFD32F2F); graphics.drawCenteredString(client.font, sameDimension ? Math.round(distance) + " 格" : "请先进入目标维度", screenWidth / 2, top + 23, 0xFFFFFF);
		if (!sameDimension) return; double bearing = relativeBearing(client.player.getX(), client.player.getZ(), client.player.getYRot(), target.x, target.z); String turn = turn(bearing); graphics.drawCenteredString(client.font, turn, screenWidth / 2, top + 38, 0x80D8FF);
		if (distance > 12 && Math.abs(bearing) >= 60) { int y = Math.max(70, screenHeight / 3); PoseStack pose = graphics.pose(); pose.pushPose(); pose.translate(screenWidth / 2.0, y, 0); pose.scale(2, 2, 1); graphics.drawCenteredString(client.font, "您已偏航", 0, 0, 0xFF3030); pose.popPose(); graphics.drawCenteredString(client.font, "正确方向：" + cardinal(client.player.getX(), client.player.getZ(), target.x, target.z) + " · " + turn, screenWidth / 2, y + 22, 0xFFFFFF); }
	}

	private static String label(String type) { return switch (type) { case "biome" -> "群系"; case "structure" -> "结构"; case "dimension" -> "维度"; case "all" -> "全部"; default -> "目标"; }; }
	private static double relativeBearing(double x, double z, float yaw, double tx, double tz) { double value = Math.toDegrees(Math.atan2(-(tx - x), tz - z)) - yaw; value %= 360; if (value >= 180) value -= 360; if (value < -180) value += 360; return value; }
	private static String turn(double b) { if (b < -157.5 || b >= 157.5) return "▼ 后方"; if (b < -112.5) return "↙ 左后方"; if (b < -67.5) return "← 左转"; if (b < -22.5) return "↖ 左前方"; if (b < 22.5) return "▲ 直行"; if (b < 67.5) return "↗ 右前方"; if (b < 112.5) return "→ 右转"; return "↘ 右后方"; }
	private static String cardinal(double x, double z, double tx, double tz) { double angle = Math.toDegrees(Math.atan2(tz - z, tx - x)); if (angle < 0) angle += 360; if (angle < 22.5 || angle >= 337.5) return "东"; if (angle < 67.5) return "东南"; if (angle < 112.5) return "南"; if (angle < 157.5) return "西南"; if (angle < 202.5) return "西"; if (angle < 247.5) return "西北"; if (angle < 292.5) return "北"; return "东北"; }

	private static final class NavigatorScreen extends Screen {
		private final Screen parent; private String query = "", type = "all"; private int page, observed;
		private NavigatorScreen(Screen parent) { super(Component.literal("方向导航 · 群系与结构")); this.parent = parent; }
		@Override protected void init() { observed = revision; rebuild(); }
		@Override public void tick() { if (observed != revision) { observed = revision; rebuild(); } }
		private void rebuild() {
			clearWidgets(); int panel = Math.min(720, width - 24), left = (width - panel) / 2;
			EditBox search = new EditBox(font, left, 35, panel - 190, 20, Component.literal("搜索")); search.setValue(query); search.setMaxLength(120); search.setResponder(v -> query = v); addRenderableWidget(search);
			addRenderableWidget(Button.builder(Component.literal("搜索"), b -> { page = 0; rebuild(); }).bounds(left + panel - 182, 35, 62, 20).build());
			addRenderableWidget(Button.builder(Component.literal("类型：" + label(type)), b -> { type = switch (type) { case "all" -> "biome"; case "biome" -> "structure"; case "structure" -> "dimension"; default -> "all"; }; page = 0; rebuild(); }).bounds(left + panel - 112, 35, 112, 20).build());
			List<LegacyForgeNavigation.Entry> filtered = entries.stream().filter(v -> type.equals("all") || v.type().equals(type)).filter(v -> query.isBlank() || v.id().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(LegacyForgeNavigation.Entry::type).thenComparing(LegacyForgeNavigation.Entry::id)).toList();
			int pageSize = Math.max(4, Math.min(10, (height - 145) / 23)), pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize); page = Math.max(0, Math.min(page, pages - 1)); int y = 64;
			for (LegacyForgeNavigation.Entry entry : filtered.stream().skip((long) page * pageSize).limit(pageSize).toList()) { addRenderableWidget(Button.builder(Component.literal(label(entry.type()) + " · " + entry.id()), b -> { LegacyForgeNavigation.requestLocate(entry.type(), entry.id()); message = "正在服务端搜索目标……"; revision++; minecraft.setScreen(parent); }).bounds(left, y, panel, 20).build()); y += 23; }
			addRenderableWidget(Button.builder(Component.literal("取消当前导航"), b -> { LegacyForgeNavigation.requestCancel(); message = "正在回收临时导航物品……"; revision++; }).bounds(left, height - 50, 150, 20).build());
			addRenderableWidget(Button.builder(Component.literal("刷新目录"), b -> LegacyForgeNavigation.requestCatalog()).bounds(left + 158, height - 50, 110, 20).build());
			if (pages > 1) { addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; rebuild(); }).bounds(left, height - 26, 90, 20).build()); addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; rebuild(); }).bounds(left + 98, height - 26, 90, 20).build()); }
			addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(left + panel - 90, height - 50, 90, 20).build());
		}
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF); graphics.drawCenteredString(font, message + " · 共 " + entries.size() + " 项", width / 2, height - 66, 0xA8E6A3); }
	}

	private record Target(String type, String id, String dimension, double x, double y, double z, double starting) { }
}
