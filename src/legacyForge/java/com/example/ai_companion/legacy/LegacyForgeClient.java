package com.example.ai_companion.legacy;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Directly-polled 1.20.1 shortcuts. They intentionally stay out of the vanilla Controls list. */
@Mod.EventBusSubscriber(modid = LegacyForgeMod.MOD_ID, value = Dist.CLIENT)
public final class LegacyForgeClient {
	private static boolean navigationTop = true;
	private static boolean sprintJumpEnabled = true;
	private static boolean positionsVisible;
	private static boolean comboDown, positionsDown, zoomDown, navigationDown, sprintJumpLatched;
	private static Integer savedFov;

	@SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft client = Minecraft.getInstance();
		long window = client.getWindow().getWindow();
		boolean v = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_V);
		boolean b = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_B);
		boolean f8 = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F8);
		boolean c = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_C);
		boolean g = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_G);
		if (v && b && !comboDown && client.screen == null) client.setScreen(new CompanionScreen(false, null));
		if (f8 && !positionsDown && client.player != null && client.getConnection() != null) client.getConnection().sendCommand("aiplayer positions");
		positionsVisible = f8;
		if (sprintJumpEnabled && client.player != null && client.options.keyUp.isDown() && !client.player.isShiftKeyDown()) client.player.setSprinting(true);
		if (g && !navigationDown && client.screen == null) client.setScreen(new CompanionScreen(true, null));
		if (c && !zoomDown) { savedFov = client.options.fov().get(); client.options.fov().set(Math.max(30, savedFov / 4)); }
		if (!c && zoomDown && savedFov != null) { client.options.fov().set(savedFov); savedFov = null; }
		if (client.player != null) {
			if (client.player.onGround()) sprintJumpLatched = sprintJumpEnabled && client.player.isSprinting();
			else if (sprintJumpEnabled && sprintJumpLatched) client.player.setSprinting(true);
			else if (!sprintJumpEnabled) sprintJumpLatched = false;
		}
		comboDown = v && b; positionsDown = f8; zoomDown = c; navigationDown = g;
	}
	@SubscribeEvent public static void renderOverlay(RenderGuiOverlayEvent.Post event) {
		if (!positionsVisible) return; Minecraft client = Minecraft.getInstance(); GuiGraphics graphics = event.getGuiGraphics();
		int panelWidth = Math.min(520, client.getWindow().getGuiScaledWidth() - 30); int left = (client.getWindow().getGuiScaledWidth() - panelWidth) / 2;
		graphics.fill(left, 8, left + panelWidth, 48, 0xD010151B);
		graphics.drawCenteredString(client.font, "AI 玩家列表 · 松开 F8 关闭", client.getWindow().getGuiScaledWidth() / 2, 14, 0xFFFFFF);
		graphics.drawCenteredString(client.font, "已刷新服务器位置；完整坐标由服务器返回", client.getWindow().getGuiScaledWidth() / 2, 31, 0xB3E5FC);
	}

	public static Screen configScreen(Screen parent) { return new CompanionScreen(false, parent); }

	@Mod.EventBusSubscriber(modid = LegacyForgeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static final class ModBus {
		private ModBus() { }
		@SubscribeEvent public static void clientSetup(FMLClientSetupEvent event) {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> configScreen(parent)));
		}
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
		private final Screen parent;
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
		private String apiEndpoint = "https://api.openai.com/v1";
		private String apiModel = "gpt-5-mini";
		private String apiToken = "";
		private String status = "小游戏纯本地运行；1.20.1 服务端操作使用兼容回退";
		private CompanionScreen(boolean navigation, Screen parent) {
			super(Component.literal(navigation ? "AI 导航" : "WindowsdePC's AI Companion Mod"));
			this.navigation = navigation;
			this.parent = parent;
		}
		@Override protected void init() {
			rebuild();
		}

		private void rebuild() {
			clearWidgets();
			int fullWidth = Math.min(760, width - 20);
			int left = (width - fullWidth) / 2;
			int sidebar = 0;
			if (navigation) {
				button("查询 AI 位置作为导航目标", "aiplayer positions", width / 2 - 130,
					height / 2 - 10, 260);
			} else {
				int panel; int panelWidth;
				if (navigationTop) { int tabWidth = Math.max(64, (fullWidth - 16) / Tab.values().length); int x = left; for (Tab value : Tab.values()) { addRenderableWidget(Button.builder(Component.literal((value == tab ? "●" : "") + value.label), button -> { tab = value; rebuild(); }).bounds(x, 25, tabWidth, 20).build()); x += tabWidth + 2; } panel = left; panelWidth = fullWidth; }
				else { int y = 28; for (Tab value : Tab.values()) { addRenderableWidget(Button.builder(Component.literal((value == tab ? "▶ " : "  ") + value.label), button -> { tab = value; rebuild(); }).bounds(left, y, sidebar, 20).build()); y += 22; } panel = left + sidebar + 10; panelWidth = fullWidth - sidebar - 10; }
				buildTab(panel, panelWidth);
			}
			addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(left + fullWidth - 90, height - 24, 90, 20).build());
		}

		private void buildTab(int left, int panelWidth) {
			switch (tab) {
				case AI -> buildAi(left, panelWidth);
				case SHORTCUTS -> { addRenderableWidget(Button.builder(Component.literal("UI：V+B · AI菜单：F8 · 缩放：C · 导航：G"), b -> {}).bounds(left, 65, panelWidth, 20).build()); addRenderableWidget(Button.builder(Component.literal("配置快捷栏：" + (navigationTop ? "顶部" : "左侧")), b -> { navigationTop = !navigationTop; rebuild(); }).bounds(left, 93, panelWidth, 20).build()); status = "快捷键项目已显示；1.20.1 使用直接按键轮询"; }
				case GAMEPLAY -> { addRenderableWidget(Button.builder(Component.literal("持续疾跑跳跃：" + (sprintJumpEnabled ? "开启" : "关闭")), b -> { sprintJumpEnabled = !sprintJumpEnabled; rebuild(); }).bounds(left, 65, panelWidth, 20).build()); button("查看 1.20.1 可用功能", "aiplayer feature status", left, 93, panelWidth); }
				case CLIENT -> buildSpyglass(left, panelWidth);
				case MINIGAMES -> {
					addRenderableWidget(Button.builder(Component.literal("打开本地反应训练小游戏"), value ->
						minecraft.setScreen(new LocalMinigameScreen(this))).bounds(left, 55, panelWidth, 22).build());
					button("AI 宠物：我的宠物", "aiplayer pet list", left, 87, (panelWidth - 8) / 2);
					button("AI 宠物：排行榜", "aiplayer pet leaderboard", left + (panelWidth + 8) / 2, 87, (panelWidth - 8) / 2);
					button("AI 竞技：查看可用 AI", "aiplayer list", left, 119, panelWidth);
					status = "本地小游戏已加入独立弹窗；AI 宠物与 AI 竞技入口已加入当前栏目";
				}
				case LEISURE -> {
					button("模拟社会排行榜", "aiplayer society leaderboard", left, 65, panelWidth);
					button("自然事件状态", "aiplayer weather status", left, 93, panelWidth);
					button("自然事件历史", "aiplayer weather history 5", left, 121, panelWidth);
				}
				case PERFORMANCE -> status = "1.20.1 兼容版不混入 26.2 客户端渲染优化 API";
				case COMPATIBILITY -> buildCompatibility(left, panelWidth);
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
			button("传送至 " + agentName + "（管理员）", "aiplayer teleport-to " + agentName, left, 139, panelWidth);
		}

		private void buildCompatibility(int left, int panelWidth) {
			EditBox endpoint = new EditBox(font, left, 58, panelWidth, 20, Component.literal("API 地址"));
			endpoint.setMaxLength(300); endpoint.setValue(apiEndpoint); endpoint.setResponder(value -> apiEndpoint = value); addRenderableWidget(endpoint);
			EditBox model = new EditBox(font, left, 86, panelWidth / 2 - 4, 20, Component.literal("模型"));
			model.setMaxLength(100); model.setValue(apiModel); model.setResponder(value -> apiModel = value); addRenderableWidget(model);
			EditBox token = new EditBox(font, left + panelWidth / 2 + 4, 86, panelWidth / 2 - 4, 20, Component.literal("API 令牌"));
			token.setMaxLength(500); token.setValue(apiToken); token.setResponder(value -> apiToken = value); addRenderableWidget(token);
			addRenderableWidget(Button.builder(Component.literal("保存 API 配置"), button -> {
				command("aiplayer config endpoint " + apiEndpoint);
				command("aiplayer config model " + apiModel);
				if (!apiToken.isBlank()) command("aiplayer config token " + apiToken);
				apiToken = "";
			}).bounds(left, 114, (panelWidth - 8) / 2, 20).build());
			button("检查 API 配置", "aiplayer config status", left + (panelWidth + 8) / 2, 114, (panelWidth - 8) / 2);
			status = "1.20.1 Forge 使用原生模组配置入口；服务器不支持UI包时回退到兼容命令";
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
		@Override public void onClose() {
			if (minecraft != null && parent != null) minecraft.setScreen(parent);
			else super.onClose();
		}
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, navigation ? "G：1.20.1 导航入口" : "WindowsdePC's AI Companion Mod · 1.20.1 Forge 完整管理", width / 2, 10, 0xFFFFFF);
			graphics.drawString(font, status, 10, height - 36, 0xA8E6A3);
		}
	}

	/** Small 1.20.1-only local game; it never contacts a server. */
	private static final class LocalMinigameScreen extends Screen {
		private final Screen parent;
		private int score;
		private int targetX;
		private int targetY;
		private LocalMinigameScreen(Screen parent) { super(Component.literal("本地反应训练")); this.parent = parent; }
		@Override protected void init() { moveTarget(); }
		private void moveTarget() {
			clearWidgets();
			targetX = 20 + (int) (Math.random() * Math.max(1, width - 140));
			targetY = 45 + (int) (Math.random() * Math.max(1, height - 120));
			addRenderableWidget(Button.builder(Component.literal("点击目标 +1"), b -> { score++; moveTarget(); })
				.bounds(targetX, targetY, 120, 20).build());
			addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), b -> onClose())
				.bounds(width / 2 - 70, height - 28, 140, 20).build());
		}
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, "纯本地反应训练 · 得分 " + score, width / 2, 16, 0xFFFFFF);
		}
	}
}
