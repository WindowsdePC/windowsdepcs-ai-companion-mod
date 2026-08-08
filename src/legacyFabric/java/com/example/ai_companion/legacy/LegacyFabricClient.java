package com.example.ai_companion.legacy;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Directly-polled 1.20.1 shortcuts. They intentionally stay out of the vanilla Controls list. */
public final class LegacyFabricClient implements ClientModInitializer {
	private boolean comboDown, positionsDown, zoomDown, navigationDown;
	private Integer savedFov;

	@Override public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		long window = client.getWindow().getWindow();
		boolean v = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_V);
		boolean b = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_B);
		boolean f8 = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F8);
		boolean c = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_C);
		boolean g = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_G);
		if (v && b && !comboDown && client.screen == null) client.setScreen(new CompanionScreen(false));
		if (f8 && !positionsDown && client.player != null && client.getConnection() != null) client.getConnection().sendCommand("aiplayer positions");
		if (g && !navigationDown && client.screen == null) client.setScreen(new CompanionScreen(true));
		if (c && !zoomDown) { savedFov = client.options.fov().get(); client.options.fov().set(Math.max(30, savedFov / 4)); }
		if (!c && zoomDown && savedFov != null) { client.options.fov().set(savedFov); savedFov = null; }
		comboDown = v && b; positionsDown = f8; zoomDown = c; navigationDown = g;
	}

	private static final class CompanionScreen extends Screen {
		private enum Tab {
			AI("AI系统"), SHORTCUTS("快捷键修改"), GAMEPLAY("游戏增强"), CLIENT("客户端增强"),
			MINIGAMES("小游戏中心"), LEISURE("休闲系统"), PERFORMANCE("性能优化"),
			COMPATIBILITY("兼容设置"), ADVANCED("高级设置");
			final String label;
			Tab(String label) { this.label = label; }
		}

		private final boolean navigation;
		private Tab tab = Tab.AI;
		private String agentName = "AI_1";
		private String prefix = "AI_";
		private String count = "2";
		private boolean spyglassEnabled = true;
		private String spyglassRadius = "10";
		private String spyglassHold = "1";
		private String spyglassDuration = "120";
		private String spyglassTarget = "all_living";
		private String spyglassCooldown = "10";
		private String spyglassMaxTargets = "256";
		private String status = "所有按钮都会向服务器发送真实命令";
		private CompanionScreen(boolean navigation) { super(Component.literal(navigation ? "AI 导航" : "WindowsdePC's AI Companion Mod")); this.navigation = navigation; }
		@Override protected void init() {
			rebuild();
		}

		private void rebuild() {
			clearWidgets();
			int fullWidth = Math.min(760, width - 20);
			int left = (width - fullWidth) / 2;
			int sidebar = 122;
			if (navigation) {
				button("查询 AI 位置作为导航目标", "aiplayer positions", width / 2 - 130,
					height / 2 - 10, 260);
			} else {
				int y = 28;
				for (Tab value : Tab.values()) {
					addRenderableWidget(Button.builder(Component.literal((value == tab ? "▶ " : "  ") + value.label),
						button -> { tab = value; rebuild(); }).bounds(left, y, sidebar, 20).build());
					y += 22;
				}
				int panel = left + sidebar + 10;
				int panelWidth = fullWidth - sidebar - 10;
				buildTab(panel, panelWidth);
			}
			addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(left + fullWidth - 90, height - 24, 90, 20).build());
		}

		private void buildTab(int left, int panelWidth) {
			switch (tab) {
				case AI -> buildAi(left, panelWidth);
				case SHORTCUTS -> status = "V+B 打开管理；F8 查询位置；按住 C 缩放；G 打开导航";
				case GAMEPLAY -> button("查看 1.20.1 可用功能", "aiplayer feature status", left, 65, panelWidth);
				case CLIENT -> buildSpyglass(left, panelWidth);
				case MINIGAMES -> {
					button("竞技宠物列表", "aiplayer pet list", left, 65, panelWidth);
					button("竞技宠物排行榜", "aiplayer pet leaderboard", left, 93, panelWidth);
				}
				case LEISURE -> {
					button("模拟社会排行榜", "aiplayer society leaderboard", left, 65, panelWidth);
					button("自然事件状态", "aiplayer weather status", left, 93, panelWidth);
					button("自然事件历史", "aiplayer weather history 5", left, 121, panelWidth);
				}
				case PERFORMANCE -> status = "1.20.1 兼容版不混入 26.2 客户端渲染优化 API";
				case COMPATIBILITY -> {
					button("检查兼容状态", "aiplayer compatibility", left, 65, panelWidth);
					button("检查 API 配置", "aiplayer config status", left, 93, panelWidth);
				}
				case ADVANCED -> {
					button("自然事件配置", "aiplayer weather config status", left, 65, panelWidth);
					button("自然事件日程", "aiplayer weather schedule list", left, 93, panelWidth);
				}
			}
		}

		private void buildAi(int left, int panelWidth) {
			EditBox name = new EditBox(font, left, 55, Math.max(120, panelWidth - 125), 20, Component.literal("AI 名称"));
			name.setValue(agentName); name.setResponder(value -> agentName = value); addRenderableWidget(name);
			addRenderableWidget(Button.builder(Component.literal("生成 AI"), button -> command("aiplayer create " + agentName))
				.bounds(left + panelWidth - 115, 55, 115, 20).build());
			EditBox prefixBox = new EditBox(font, left, 83, Math.max(100, panelWidth - 190), 20, Component.literal("批量前缀"));
			prefixBox.setValue(prefix); prefixBox.setResponder(value -> prefix = value); addRenderableWidget(prefixBox);
			EditBox countBox = new EditBox(font, left + panelWidth - 180, 83, 55, 20, Component.literal("数量"));
			countBox.setValue(count); countBox.setResponder(value -> count = value); addRenderableWidget(countBox);
			addRenderableWidget(Button.builder(Component.literal("批量生成"), button -> command("aiplayer create-many " + count + " " + prefix))
				.bounds(left + panelWidth - 115, 83, 115, 20).build());
			button("AI 列表", "aiplayer list", left, 111, (panelWidth - 8) / 2);
			button("AI 位置", "aiplayer positions", left + (panelWidth + 8) / 2, 111, (panelWidth - 8) / 2);
		}

		private void buildSpyglass(int left, int panelWidth) {
			addRenderableWidget(Button.builder(Component.literal("望远镜生物发光：" + (spyglassEnabled ? "开启" : "关闭")),
				button -> { spyglassEnabled = !spyglassEnabled; rebuild(); }).bounds(left, 45, panelWidth, 20).build());
			EditBox radius = new EditBox(font, left, 73, (panelWidth - 16) / 3, 20, Component.literal("半径区块"));
			radius.setValue(spyglassRadius); radius.setResponder(value -> spyglassRadius = value); addRenderableWidget(radius);
			EditBox hold = new EditBox(font, left + (panelWidth + 8) / 3, 73, (panelWidth - 16) / 3, 20, Component.literal("观察秒数"));
			hold.setValue(spyglassHold); hold.setResponder(value -> spyglassHold = value); addRenderableWidget(hold);
			EditBox duration = new EditBox(font, left + 2 * (panelWidth + 8) / 3, 73, (panelWidth - 16) / 3, 20, Component.literal("持续秒数"));
			duration.setValue(spyglassDuration); duration.setResponder(value -> spyglassDuration = value); addRenderableWidget(duration);
			addRenderableWidget(Button.builder(Component.literal("目标：" + switch (spyglassTarget) {
				case "non_players" -> "仅非玩家生物"; case "hostile_only" -> "仅敌对生物"; default -> "全部生物"; }),
				button -> { spyglassTarget = switch (spyglassTarget) {
					case "all_living" -> "non_players"; case "non_players" -> "hostile_only"; default -> "all_living"; }; rebuild();
				}).bounds(left, 105, panelWidth, 20).build());
			EditBox cooldown = new EditBox(font, left, 133, (panelWidth - 8) / 2, 20, Component.literal("触发冷却秒数"));
			cooldown.setValue(spyglassCooldown); cooldown.setResponder(value -> spyglassCooldown = value); addRenderableWidget(cooldown);
			EditBox maxTargets = new EditBox(font, left + (panelWidth + 8) / 2, 133, (panelWidth - 8) / 2, 20, Component.literal("单次命中上限"));
			maxTargets.setValue(spyglassMaxTargets); maxTargets.setResponder(value -> spyglassMaxTargets = value); addRenderableWidget(maxTargets);
			addRenderableWidget(Button.builder(Component.literal("保存望远镜设置"), button -> {
				command("aiplayer spyglass enabled " + spyglassEnabled);
				command("aiplayer spyglass radius-chunks " + spyglassRadius);
				command("aiplayer spyglass hold-seconds " + spyglassHold);
				command("aiplayer spyglass duration-seconds " + spyglassDuration);
				command("aiplayer spyglass target " + spyglassTarget);
				command("aiplayer spyglass cooldown-seconds " + spyglassCooldown);
				command("aiplayer spyglass max-targets " + spyglassMaxTargets);
			}).bounds(left, 161, (panelWidth - 8) / 2, 20).build());
			button("查看望远镜设置", "aiplayer spyglass status", left + (panelWidth + 8) / 2, 161, (panelWidth - 8) / 2);
			status = "默认：观察1秒，10区块，发光120秒，冷却10秒，单次最多256个";
		}

		private void button(String label, String command, int x, int y, int buttonWidth) {
			addRenderableWidget(Button.builder(Component.literal(label), value -> command(command))
				.bounds(x, y, buttonWidth, 20).build());
		}

		private void command(String value) {
			if (minecraft == null || minecraft.getConnection() == null) {
				status = "当前没有服务器连接";
				return;
			}
			minecraft.getConnection().sendCommand(value);
			status = "已发送：/" + value + "；请查看服务器返回结果";
		}
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, navigation ? "G：1.20.1 导航入口" : "WindowsdePC's AI Companion Mod · 1.20.1 Fabric 完整管理", width / 2, 10, 0xFFFFFF);
			graphics.drawString(font, status, 10, height - 36, 0xA8E6A3);
		}
	}
}
