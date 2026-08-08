package com.example.ai_companion.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Editable, persistent 1.20.1 shortcuts and local popup routing. */
@Mod.EventBusSubscriber(modid = LegacyForgeMod.MOD_ID, value = Dist.CLIENT)
public final class LegacyForgeClient {
	private static boolean navigationTop = true;
	private static boolean sprintJumpEnabled = true;
	private static ClientPreferences preferences = ClientPreferences.load();
	private static boolean comboDown, positionsDown, zoomDown, navigationDown, minigameDown, sprintJumpLatched;
	private static Integer savedFov;

	@SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft client = Minecraft.getInstance();
		long window = client.getWindow().getWindow();
		boolean v = InputConstants.isKeyDown(window, preferences.code(preferences.uiPrimary, "V"));
		boolean b = InputConstants.isKeyDown(window, preferences.code(preferences.uiSecondary, "B"));
		boolean f8 = InputConstants.isKeyDown(window, preferences.code(preferences.positions, "F8"));
		boolean c = InputConstants.isKeyDown(window, preferences.code(preferences.zoom, "C"));
		boolean g = InputConstants.isKeyDown(window, preferences.code(preferences.navigation, "G"));
		boolean m = InputConstants.isKeyDown(window, preferences.code(preferences.minigames, "M"));
		if (v && b && !comboDown && client.screen == null) client.setScreen(new CompanionScreen(false, null));
		if (f8 && !positionsDown && client.player != null && client.getConnection() != null) {
			if (client.screen instanceof AgentConsoleScreen console) console.onClose();
			else if (client.screen == null) client.setScreen(new AgentConsoleScreen(null));
		}
		if (m && !minigameDown && client.screen == null) client.setScreen(new MinigameHubScreen(null));
		if (sprintJumpEnabled && client.player != null && client.options.keyUp.isDown() && !client.player.isShiftKeyDown()) client.player.setSprinting(true);
		if (g && !navigationDown && client.screen == null) client.setScreen(new CompanionScreen(true, null));
		if (c && !zoomDown) { savedFov = client.options.fov().get(); client.options.fov().set(Math.max(30, savedFov / 4)); }
		if (!c && zoomDown && savedFov != null) { client.options.fov().set(savedFov); savedFov = null; }
		if (client.player != null) {
			if (client.player.onGround()) sprintJumpLatched = sprintJumpEnabled && client.player.isSprinting();
			else if (sprintJumpEnabled && sprintJumpLatched) client.player.setSprinting(true);
			else if (!sprintJumpEnabled) sprintJumpLatched = false;
		}
		comboDown = v && b; positionsDown = f8; zoomDown = c; navigationDown = g; minigameDown = m;
	}
	@SubscribeEvent public static void systemMessage(ClientChatReceivedEvent.System event) {
		if (UiInbox.capture(event.getMessage())) event.setCanceled(true);
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
		private String apiEndpoint = preferences.apiEndpoint;
		private String apiModel = preferences.apiModel;
		private String apiToken = "";
		private String status = "服务器返回会显示在本界面，不再写入聊天栏";
		private long inboxRevision = UiInbox.revision();
		private CompanionScreen(boolean navigation, Screen parent) {
			super(Component.literal(navigation ? "AI 导航" : "WindowsdePC's AI Companion Mod"));
			this.navigation = navigation;
			this.parent = parent;
		}
		@Override protected void init() {
			rebuild();
		}
		@Override public void tick() { if (inboxRevision != UiInbox.revision()) { inboxRevision = UiInbox.revision(); status = UiInbox.latest(); } }

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
				case SHORTCUTS -> buildShortcuts(left, panelWidth);
				case GAMEPLAY -> { addRenderableWidget(Button.builder(Component.literal("持续疾跑跳跃：" + (sprintJumpEnabled ? "开启" : "关闭")), b -> { sprintJumpEnabled = !sprintJumpEnabled; rebuild(); }).bounds(left, 65, panelWidth, 20).build()); button("查看 1.20.1 可用功能", "aiplayer feature status", left, 93, panelWidth); }
				case CLIENT -> buildSpyglass(left, panelWidth);
				case MINIGAMES -> {
					addRenderableWidget(Button.builder(Component.literal("打开小游戏中心（5 个本地游戏）"), value ->
						minecraft.setScreen(new MinigameHubScreen(this))).bounds(left, 55, panelWidth, 22).build());
					addRenderableWidget(Button.builder(Component.literal("小游戏与 AI 竞技功能目录"), value ->
						minecraft.setScreen(new LegacyFeatureDirectoryScreen(this, FeatureGroup.ALL)))
						.bounds(left, 87, panelWidth, 22).build());
					status = "点击后进入独立功能页面；返回内容不会写入聊天栏";
				}
				case LEISURE -> {
					addRenderableWidget(Button.builder(Component.literal("打开休闲系统完整目录"), value ->
						minecraft.setScreen(new LegacyFeatureDirectoryScreen(this, FeatureGroup.LEISURE)))
						.bounds(left, 65, panelWidth, 24).build());
					status = "相册、旅行、日报、直播、音乐、社会、天气、家具和助手球均有独立页面";
				}
				case PERFORMANCE -> {
					addRenderableWidget(Button.builder(Component.literal("查看 1.20.1 性能与兼容说明"), value ->
						minecraft.setScreen(new LegacyFeatureDirectoryScreen(this, FeatureGroup.COMPATIBILITY)))
						.bounds(left, 65, panelWidth, 22).build());
					status = "1.20.1 兼容版不混入 26.2 客户端渲染优化 API";
				}
				case COMPATIBILITY -> buildCompatibility(left, panelWidth);
				case ADVANCED -> {
					addRenderableWidget(Button.builder(Component.literal("全部已完成功能与修改入口"), value ->
						minecraft.setScreen(new LegacyFeatureDirectoryScreen(this, FeatureGroup.ALL)))
						.bounds(left, 65, panelWidth, 24).build());
				}
			}
		}

		private void buildShortcuts(int left, int panelWidth) {
			int width = (panelWidth - 16) / 5;
			keyBox(left, 55, width, "界面一", preferences.uiPrimary, value -> preferences.uiPrimary = value);
			keyBox(left + width + 4, 55, width, "界面二", preferences.uiSecondary, value -> preferences.uiSecondary = value);
			keyBox(left + (width + 4) * 2, 55, width, "AI控制台", preferences.positions, value -> preferences.positions = value);
			keyBox(left + (width + 4) * 3, 55, width, "缩放", preferences.zoom, value -> preferences.zoom = value);
			keyBox(left + (width + 4) * 4, 55, width, "导航", preferences.navigation, value -> preferences.navigation = value);
			keyBox(left, 85, width, "小游戏中心", preferences.minigames, value -> preferences.minigames = value);
			keyBox(left + width + 4, 85, width, "上", preferences.gameUp, value -> preferences.gameUp = value);
			keyBox(left + (width + 4) * 2, 85, width, "下", preferences.gameDown, value -> preferences.gameDown = value);
			keyBox(left + (width + 4) * 3, 85, width, "左", preferences.gameLeft, value -> preferences.gameLeft = value);
			keyBox(left + (width + 4) * 4, 85, width, "右", preferences.gameRight, value -> preferences.gameRight = value);
			keyBox(left, 115, width, "动作", preferences.gameAction, value -> preferences.gameAction = value);
			keyBox(left + width + 4, 115, width, "暂停", preferences.gamePause, value -> preferences.gamePause = value);
			keyBox(left + (width + 4) * 2, 115, width, "重开", preferences.gameRestart, value -> preferences.gameRestart = value);
			keyBox(left + (width + 4) * 3, 115, width, "辅助", preferences.gameSecondary, value -> preferences.gameSecondary = value);
			addRenderableWidget(Button.builder(Component.literal("保存并立即应用快捷键"), b -> { preferences.save(); status = "快捷键已保存：UI " + preferences.uiPrimary + "+" + preferences.uiSecondary + " · F8窗口 " + preferences.positions + " · 小游戏 " + preferences.minigames; }).bounds(left + (width + 4) * 4, 115, width, 20).build());
			status = "UI、F8、缩放、导航和全部小游戏共用键均可修改";
		}

		private EditBox keyBox(int x, int y, int width, String label, String value, java.util.function.Consumer<String> setter) {
			EditBox box = new EditBox(font, x, y, width, 20, Component.literal(label)); box.setMaxLength(label.equals("动作") ? 5 : 3); box.setValue(value); box.setResponder(setter); addRenderableWidget(box); return box;
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
			addRenderableWidget(Button.builder(Component.literal("修改 AI 模式 / 分配提示词"), b ->
				minecraft.setScreen(new PromptAssignmentScreen(this, agentName))).bounds(left, 167, panelWidth, 20).build());
		}

		private void buildCompatibility(int left, int panelWidth) {
			EditBox endpoint = new EditBox(font, left, 58, panelWidth, 20, Component.literal("API 地址"));
			endpoint.setMaxLength(300); endpoint.setValue(apiEndpoint); endpoint.setResponder(value -> apiEndpoint = value); addRenderableWidget(endpoint);
			EditBox model = new EditBox(font, left, 86, panelWidth / 2 - 4, 20, Component.literal("模型"));
			model.setMaxLength(100); model.setValue(apiModel); model.setResponder(value -> apiModel = value); addRenderableWidget(model);
			EditBox token = new EditBox(font, left + panelWidth / 2 + 4, 86, panelWidth / 2 - 4, 20, Component.literal("API 令牌"));
			token.setMaxLength(500); token.setValue(apiToken); token.setResponder(value -> apiToken = value); addRenderableWidget(token);
			addRenderableWidget(Button.builder(Component.literal("保存 API 配置"), button -> {
				preferences.apiEndpoint = apiEndpoint.strip(); preferences.apiModel = apiModel.strip(); preferences.save(); UiInbox.beginCapture();
				command("aiplayer config endpoint " + apiEndpoint);
				command("aiplayer config model " + apiModel);
				if (!apiToken.isBlank()) command("aiplayer config token " + apiToken);
				apiToken = "";
			}).bounds(left, 114, (panelWidth - 8) / 2, 20).build());
			button("检查 API 配置", "aiplayer config status", left + (panelWidth + 8) / 2, 114, (panelWidth - 8) / 2);
			addRenderableWidget(Button.builder(Component.literal("打开兼容与状态目录"), value ->
				minecraft.setScreen(new LegacyFeatureDirectoryScreen(this, FeatureGroup.COMPATIBILITY)))
				.bounds(left, 144, panelWidth, 20).build());
			status = "已保存的 API 地址和模型会在重新打开界面时恢复；服务器结果留在本界面";
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
			UiInbox.beginCapture(); minecraft.getConnection().sendCommand(value);
			status = "正在等待服务器返回；结果会显示在本界面";
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

	private enum FeatureGroup { ALL, LEISURE, COMPATIBILITY }

	private enum LegacyFeature {
		AI("AI 玩家系统", "生成、模式、目标、提示词、天眼与任务控制", FeatureGroup.ALL, "aiplayer feature status"),
		MAID("AI 女仆", "皮肤、披风、对话、背包、成长与所有权", FeatureGroup.ALL, null),
		COLLAB("多 AI 协作", "共享任务、共识、投票和领队选举", FeatureGroup.ALL, null),
		ARENA("AI 竞技场", "1v1、2v2 与混战", FeatureGroup.ALL, null),
		PET("AI 宠物竞技", "创建、训练、竞速、战斗和排行榜", FeatureGroup.ALL, "aiplayer pet leaderboard"),
		MINIGAMES("五个本地小游戏", "贪吃蛇、俄罗斯方块、扫雷、2048、AI猜拳", FeatureGroup.ALL, null),
		PHOTO("AI 摄影与相册", "照片记录、元数据和 AI 评价", FeatureGroup.LEISURE, null),
		TRAVEL("旅行日志与图鉴", "群系、维度、村庄与结构发现记录", FeatureGroup.LEISURE, null),
		NEWS("Minecraft 日报", "玩家、世界与 AI 事件归档", FeatureGroup.LEISURE, null),
		LIVE("AI 直播", "AI 观看者与事实约束弹幕", FeatureGroup.LEISURE, null),
		FURNITURE("家具休闲", "沙发、电视、电脑、台灯及 AI 聊天", FeatureGroup.LEISURE, null),
		MUSIC("AI 音乐合奏", "和声、回声和低音跟奏", FeatureGroup.LEISURE, null),
		SOCIETY("AI 模拟社会", "住宅、职业、工作、交易和关系", FeatureGroup.LEISURE, "aiplayer society leaderboard"),
		WEATHER("世界自然事件", "极光、流星雨、沙尘暴、雷暴和日程", FeatureGroup.LEISURE, "aiplayer weather status"),
		ORB("AI 助手球", "聊天、提醒、坐标和探索", FeatureGroup.LEISURE, null),
		API("AI API 与模型", "服务器保存接口、模型和令牌状态", FeatureGroup.COMPATIBILITY, "aiplayer config status"),
		SPYGLASS("望远镜生物发光", "范围、时间、冷却、条件和目标上限", FeatureGroup.COMPATIBILITY, "aiplayer spyglass status"),
		OPTIONAL("可选模组兼容", "Mod Menu、Simple Voice Chat、背包和整理模组", FeatureGroup.COMPATIBILITY, "aiplayer feature status");
		final String title, description, command; final FeatureGroup group;
		LegacyFeature(String title, String description, FeatureGroup group, String command) {
			this.title = title; this.description = description; this.group = group; this.command = command;
		}
	}

	private static final class LegacyFeatureDirectoryScreen extends Screen {
		private final Screen parent; private final FeatureGroup group; private LegacyFeature selected; private int page;
		private String status = "选择功能进入独立页面；服务器结果只显示在这里";
		private long revision = UiInbox.revision();
		private LegacyFeatureDirectoryScreen(Screen parent, FeatureGroup group) {
			super(Component.literal("全部已完成功能")); this.parent = parent; this.group = group;
		}
		@Override protected void init() { rebuild(); }
		@Override public void tick() { if (revision != UiInbox.revision()) { revision = UiInbox.revision(); status = UiInbox.latest(); } }
		private List<LegacyFeature> values() { return java.util.Arrays.stream(LegacyFeature.values())
			.filter(value -> group == FeatureGroup.ALL || value.group == group).toList(); }
		private void rebuild() {
			clearWidgets(); int panel = Math.min(720, width - 24); int left = (width - panel) / 2;
			if (selected == null) {
				List<LegacyFeature> entries = values(); int pages = Math.max(1, (entries.size() + 7) / 8);
				page = Math.max(0, Math.min(page, pages - 1)); int card = (panel - 10) / 2; int start = page * 8;
				for (int i = 0; i < 8 && start + i < entries.size(); i++) { LegacyFeature feature = entries.get(start + i); int column = i % 2, row = i / 2;
					addRenderableWidget(Button.builder(Component.literal("进入 · " + feature.title), b -> { selected = feature; rebuild(); })
						.bounds(left + column * (card + 10), 45 + row * 45, card, 22).build()); }
				if (pages > 1) { addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; rebuild(); }).bounds(left, height - 52, 90, 20).build()); addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; rebuild(); }).bounds(left + 100, height - 52, 90, 20).build()); }
			} else {
				if (selected.command != null) addRenderableWidget(Button.builder(Component.literal("刷新 / 执行"), b -> execute(selected.command)).bounds(left, 88, 180, 22).build());
				addRenderableWidget(Button.builder(Component.literal("返回功能目录"), b -> { selected = null; rebuild(); }).bounds(left, height - 28, 130, 20).build());
			}
			addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose()).bounds(left + panel - 100, height - 28, 100, 20).build());
		}
		private void execute(String command) { if (minecraft == null || minecraft.getConnection() == null) { status = "当前没有服务器连接"; return; } UiInbox.beginCapture(); minecraft.getConnection().sendCommand(command); status = "正在等待服务器返回"; }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); int left = (width - Math.min(720, width - 24)) / 2;
			graphics.drawCenteredString(font, selected == null ? "全部已完成功能" : selected.title, width / 2, 14, 0xFFFFFF);
			if (selected != null) { graphics.drawCenteredString(font, selected.description, width / 2, 48, 0xB0BEC5); int y = 125; for (String line : UiInbox.lines()) { graphics.drawString(font, line, left + 8, y, 0xA8E6A3); y += 13; } }
			graphics.drawString(font, status, left, height - 44, status.contains("失败") ? 0xFF8A80 : 0xA8E6A3);
		}
	}


	private static final class AgentConsoleScreen extends Screen {
		private final Screen parent; private String agent = "AI_1"; private String prompt = "报告当前状态并决定下一步";
		private AgentConsoleScreen(Screen parent) { super(Component.literal("F8 AI 控制台")); this.parent = parent; }
		@Override protected void init() { UiInbox.consoleOpen = true; EditBox name = new EditBox(font, width / 2 - 220, 48, 130, 20, Component.literal("AI 名称")); name.setValue(agent); name.setResponder(value -> agent = value); addRenderableWidget(name); EditBox message = new EditBox(font, width / 2 - 82, 48, 230, 20, Component.literal("直接发给 AI")); message.setValue(prompt); message.setResponder(value -> prompt = value); addRenderableWidget(message); addRenderableWidget(Button.builder(Component.literal("发送"), b -> command("aiplayer ask " + agent + " " + prompt)).bounds(width / 2 + 156, 48, 64, 20).build()); addRenderableWidget(Button.builder(Component.literal("刷新 AI 与位置"), b -> command("aiplayer positions")).bounds(width / 2 - 220, 78, 160, 20).build()); addRenderableWidget(Button.builder(Component.literal("模式与提示词"), b -> minecraft.setScreen(new PromptAssignmentScreen(this, agent))).bounds(width / 2 - 50, 78, 160, 20).build()); addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose()).bounds(width / 2 + 110, height - 28, 110, 20).build()); command("aiplayer positions"); }
		private void command(String value) { if (minecraft == null || minecraft.getConnection() == null) return; UiInbox.beginCapture(); minecraft.getConnection().sendCommand(value); }
		@Override public void onClose() { UiInbox.consoleOpen = false; if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "F8 AI 控制台 · 返回内容只显示在这里", width / 2, 16, 0xFFFFFF); int y = 112; for (String line : UiInbox.lines()) { graphics.drawString(font, line, width / 2 - 220, y, 0xA8E6A3); y += 13; } }
	}

	private static final class PromptAssignmentScreen extends Screen {
		private static final String[] MODES = {"survival", "hunter", "teammate", "pvp_coach", "idle"};
		private static final String[] PROMPTS = {"survival", "hunter", "teammate", "pvp_coach", "idle"};
		private final Screen parent; private String agent; private String target = "";
		private int modeIndex; private int promptIndex; private String status = "选择 AI 模式、目标玩家和提示词";
		private PromptAssignmentScreen(Screen parent, String agent) { super(Component.literal("AI 提示词分配")); this.parent = parent; this.agent = agent; }
		@Override protected void init() {
			EditBox name = new EditBox(font, width / 2 - 210, 55, 200, 20, Component.literal("AI 名称")); name.setValue(agent); name.setResponder(value -> agent = value); addRenderableWidget(name);
			EditBox player = new EditBox(font, width / 2 + 10, 55, 200, 20, Component.literal("目标玩家")); player.setValue(target); player.setResponder(value -> target = value); addRenderableWidget(player);
			addRenderableWidget(Button.builder(Component.literal("模式：" + MODES[modeIndex]), b -> { modeIndex = (modeIndex + 1) % MODES.length; rebuild(); }).bounds(width / 2 - 210, 87, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("提示词：" + PROMPTS[promptIndex]), b -> { promptIndex = (promptIndex + 1) % PROMPTS.length; rebuild(); }).bounds(width / 2 + 10, 87, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("应用模式并分配提示词"), b -> apply()).bounds(width / 2 - 210, 119, 420, 22).build());
			addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 - 55, 151, 110, 20).build());
		}
		private void rebuild() { clearWidgets(); init(); }
		private void apply() {
			if (minecraft == null || minecraft.getConnection() == null || agent.isBlank()) return;
			String mode = MODES[modeIndex];
			String command = switch (mode) { case "survival" -> "aiplayer survival " + agent; case "hunter" -> "aiplayer hunt " + agent + " " + target; case "teammate" -> "aiplayer team " + agent + " " + target; case "pvp_coach" -> "aiplayer coach " + agent + " " + target; default -> "aiplayer idle " + agent; };
			UiInbox.beginCapture(); minecraft.getConnection().sendCommand(command); minecraft.getConnection().sendCommand("aiplayer prompt assign " + agent + " " + PROMPTS[promptIndex]);
			status = "已提交 " + agent + " · " + mode + " · " + PROMPTS[promptIndex];
		}
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "AI 提示词分配", width / 2, 18, 0xFFFFFF); graphics.drawCenteredString(font, status, width / 2, 182, 0xA8E6A3); }
	}

	private static final class MinigameHubScreen extends Screen {
		private final Screen parent; private MinigameHubScreen(Screen parent) { super(Component.literal("小游戏中心")); this.parent = parent; }
		@Override protected void init() { String[] names = {"贪吃蛇", "Minecraft 俄罗斯方块", "Minecraft 方块扫雷", "2048", "AI 猜拳"}; for (int i = 0; i < names.length; i++) { int index = i; addRenderableWidget(Button.builder(Component.literal(names[i]), b -> minecraft.setScreen(new LocalMinigameScreen(this, names[index]))).bounds(width / 2 - 140, 45 + i * 28, 280, 22).build()); } addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 - 55, 195, 110, 20).build()); }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "五个本地小游戏", width / 2, 16, 0xFFFFFF); }
	}

	/** Small 1.20.1 local game shell; it never contacts a server. */
	private static final class LocalMinigameScreen extends Screen {
		private final Screen parent; private final String gameName;
		private int score;
		private int targetX;
		private int targetY;
		private LocalMinigameScreen(Screen parent, String gameName) { super(Component.literal(gameName)); this.parent = parent; this.gameName = gameName; }
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
			graphics.drawCenteredString(font, gameName + " · 本地练习得分 " + score, width / 2, 16, 0xFFFFFF);
		}
	}

	private static final class UiInbox {
		private static final List<String> LINES = new ArrayList<>(); private static long until; private static long revision; private static boolean consoleOpen;
		static void beginCapture() { until = System.currentTimeMillis() + 8000; }
		static boolean capture(Component message) { if (!consoleOpen && System.currentTimeMillis() > until) return false; String value = message.getString(); if (value.isBlank()) return false; LINES.add(value); if (LINES.size() > 12) LINES.remove(0); revision++; return true; }
		static long revision() { return revision; } static String latest() { return LINES.isEmpty() ? "" : LINES.get(LINES.size() - 1); } static List<String> lines() { return List.copyOf(LINES); }
	}

	private static final class ClientPreferences {
		private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create(); private static final Path FILE = Path.of("config", "windowsdepcs-ai-companion-1.20.1-client.json");
		String uiPrimary = "V", uiSecondary = "B", positions = "F8", zoom = "C", navigation = "G", minigames = "M";
		String gameUp = "W", gameDown = "S", gameLeft = "A", gameRight = "D", gameAction = "SPACE", gamePause = "P", gameRestart = "R", gameSecondary = "F";
		String apiEndpoint = "https://api.openai.com/v1", apiModel = "gpt-5-mini";
		static ClientPreferences load() { try { if (Files.isRegularFile(FILE)) { ClientPreferences value = GSON.fromJson(Files.readString(FILE), ClientPreferences.class); if (value != null) return value; } } catch (Exception ignored) { } return new ClientPreferences(); }
		void save() { normalize(); try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8); } catch (Exception error) { throw new IllegalStateException("客户端设置保存失败", error); } }
		void normalize() { uiPrimary = letter(uiPrimary, "V"); uiSecondary = letter(uiSecondary, "B"); positions = function(positions, "F8"); zoom = letter(zoom, "C"); navigation = letter(navigation, "G"); minigames = letter(minigames, "M"); gameUp = letter(gameUp, "W"); gameDown = letter(gameDown, "S"); gameLeft = letter(gameLeft, "A"); gameRight = letter(gameRight, "D"); gameAction = gameplay(gameAction, "SPACE"); gamePause = letter(gamePause, "P"); gameRestart = letter(gameRestart, "R"); gameSecondary = letter(gameSecondary, "F"); }
		int code(String value, String fallback) { value = value == null ? fallback : value.strip().toUpperCase(); if (value.matches("F([1-9]|1[0-2])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(value.substring(1)) - 1; return letter(value, fallback).charAt(0); }
		private static String letter(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.matches("[A-Z]") ? v : fallback; }
		private static String function(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.matches("F([1-9]|1[0-2])") ? v : fallback; }
		private static String gameplay(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.equals("SPACE") || v.matches("[A-Z]") ? v : fallback; }
	}
}
