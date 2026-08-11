package com.example.ai_companion.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Editable, persistent 1.20.1 shortcuts and local popup routing. */
public final class LegacyFabricClient implements ClientModInitializer {
	private static boolean navigationTop = true;
	private static boolean sprintJumpEnabled = true;
	private static ClientPreferences preferences;
	private boolean comboDown, positionsDown, zoomDown, navigationDown, minigameDown, sprintJumpLatched;
	private Integer savedFov;

	@Override public void onInitializeClient() {
		preferences = ClientPreferences.load();
		preferences.migrateLegacyDefaults();
		LegacyNavigationClient.initialize();
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !UiInbox.capture(message));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		long window = client.getWindow().getWindow();
		boolean v = preferences.uiPrimaryEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.uiPrimary, "V"));
		boolean b = preferences.uiSecondaryEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.uiSecondary, "B"));
		boolean f8 = preferences.positionsEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.positions, "F8"));
		boolean c = preferences.zoomEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.zoom, "C"));
		boolean g = preferences.navigationEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.navigation, "F7"));
		boolean m = preferences.minigamesEnabled && InputConstants.isKeyDown(window, preferences.code(preferences.minigames, "F9"));
		if (v && b && !comboDown && client.screen == null) client.setScreen(new CompanionScreen(false, null));
		if (f8 && !positionsDown && client.player != null && client.getConnection() != null) {
			if (client.screen instanceof AgentConsoleScreen console) console.onClose();
			else if (client.screen == null) client.setScreen(new AgentConsoleScreen(null));
		}
		if (m && !minigameDown && client.screen == null) client.setScreen(new MinigameHubScreen(null));
		if (sprintJumpEnabled && client.player != null && client.options.keyUp.isDown() && !client.player.isShiftKeyDown()) client.player.setSprinting(true);
		if (g && !navigationDown && client.screen == null && client.getConnection() != null) LegacyNavigationClient.open(client, null);
		if (c && !zoomDown) { savedFov = client.options.fov().get(); client.options.fov().set(Math.max(30, savedFov / 4)); }
		if (!c && zoomDown && savedFov != null) { client.options.fov().set(savedFov); savedFov = null; }
		if (client.player != null) {
			if (client.player.onGround()) sprintJumpLatched = sprintJumpEnabled && client.player.isSprinting();
			else if (sprintJumpEnabled && sprintJumpLatched) client.player.setSprinting(true);
			else if (!sprintJumpEnabled) sprintJumpLatched = false;
		}
		comboDown = v && b; positionsDown = f8; zoomDown = c; navigationDown = g; minigameDown = m;
	}

	public static Screen configScreen(Screen parent) { return new CompanionScreen(false, parent); }

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
		@Override public void tick() {
			if (inboxRevision != UiInbox.revision()) { inboxRevision = UiInbox.revision(); status = UiInbox.latest(); }
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
					addRenderableWidget(Button.builder(Component.literal("打开方向导航：群系 / 结构 / 维度"), value ->
						LegacyNavigationClient.open(minecraft, this)).bounds(left, 97, panelWidth, 24).build());
				}
			}
		}

		private void buildShortcuts(int left, int panelWidth) {
			addRenderableWidget(Button.builder(Component.literal("打开四列式快捷键管理（功能 / 开关 / 改键 / 重置）"), b ->
				minecraft.setScreen(new ShortcutSettingsScreen(this))).bounds(left, 58, panelWidth, 24).build());
			status = "小游戏中心默认 F9；缩放 C；导航 F7。旧 M/G 与 beta.4 的 F6 会安全迁移";
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
			addRenderableWidget(Button.builder(Component.literal("编辑 AI 提示词内容"), b ->
				minecraft.setScreen(new PromptEditorScreen(this))).bounds(left, 195, (panelWidth - 8) / 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal("AI 女仆任务面板"), b ->
				minecraft.setScreen(new MaidTaskScreen(this, agentName))).bounds(left + (panelWidth + 8) / 2, 195, (panelWidth - 8) / 2, 20).build());
		}

		private void buildCompatibility(int left, int panelWidth) {
			EditBox endpoint = new EditBox(font, left, 58, panelWidth, 20, Component.literal("API 地址"));
			endpoint.setMaxLength(300); endpoint.setValue(apiEndpoint); endpoint.setResponder(value -> apiEndpoint = value); addRenderableWidget(endpoint);
			EditBox model = new EditBox(font, left, 86, panelWidth / 2 - 4, 20, Component.literal("模型"));
			model.setMaxLength(100); model.setValue(apiModel); model.setResponder(value -> apiModel = value); addRenderableWidget(model);
			EditBox token = new EditBox(font, left + panelWidth / 2 + 4, 86, panelWidth / 2 - 4, 20, Component.literal("API 令牌"));
			token.setMaxLength(500); token.setValue(apiToken); token.setResponder(value -> apiToken = value); addRenderableWidget(token);
			addRenderableWidget(Button.builder(Component.literal("保存 API 配置"), button -> {
				preferences.apiEndpoint = apiEndpoint.strip(); preferences.apiModel = apiModel.strip(); preferences.save();
				UiInbox.beginCapture();
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
			UiInbox.beginCapture();
			minecraft.getConnection().sendCommand(value);
			status = "正在等待服务器返回；结果会显示在本界面";
		}
		@Override public void onClose() {
			if (minecraft != null && parent != null) minecraft.setScreen(parent);
			else super.onClose();
		}
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, navigation ? "G：1.20.1 导航入口" : "WindowsdePC's AI Companion Mod · 1.20.1 Fabric 完整管理", width / 2, 10, 0xFFFFFF);
			graphics.drawString(font, status, 10, height - 36, 0xA8E6A3);
		}
	}

	private enum FeatureGroup { ALL, LEISURE, COMPATIBILITY }

	private enum LegacyFeature {
		AI("AI 玩家系统", "生成、模式、目标、提示词、天眼与任务控制", FeatureGroup.ALL, "aiplayer feature status"),
		PROMPTS("AI 提示词编辑", "编辑中文名称对应的提示词内容并分配给 AI", FeatureGroup.ALL, null),
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
		NAVIGATION("方向导航", "全部群系、结构和维度；磁石指南针、距离条与偏航提醒", FeatureGroup.ALL, null),
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
					addRenderableWidget(Button.builder(Component.literal("进入 · " + feature.title), b -> { if (feature == LegacyFeature.MAID) minecraft.setScreen(new MaidTaskScreen(this, "AI_1")); else if (feature == LegacyFeature.PROMPTS) minecraft.setScreen(new PromptEditorScreen(this)); else if (feature == LegacyFeature.NAVIGATION) LegacyNavigationClient.open(minecraft, this); else { selected = feature; rebuild(); } })
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

	private static final class ShortcutSettingsScreen extends Screen {
		private static final int PAGE_SIZE = 9;
		private final Screen parent;
		private final List<ShortcutRow> rows = new ArrayList<>();
		private int page;
		private ShortcutRow awaiting;
		private ShortcutSettingsScreen(Screen parent) {
			super(Component.literal("快捷键管理")); this.parent = parent;
			rows.add(new ShortcutRow("主界面组合键（主键）", () -> preferences.uiPrimaryEnabled, v -> preferences.uiPrimaryEnabled = v, () -> preferences.uiPrimary, v -> preferences.uiPrimary = v, "V"));
			rows.add(new ShortcutRow("主界面组合键（副键）", () -> preferences.uiSecondaryEnabled, v -> preferences.uiSecondaryEnabled = v, () -> preferences.uiSecondary, v -> preferences.uiSecondary = v, "B"));
			rows.add(new ShortcutRow("F8 AI 控制台", () -> preferences.positionsEnabled, v -> preferences.positionsEnabled = v, () -> preferences.positions, v -> preferences.positions = v, "F8"));
			rows.add(new ShortcutRow("视野缩放", () -> preferences.zoomEnabled, v -> preferences.zoomEnabled = v, () -> preferences.zoom, v -> preferences.zoom = v, "C"));
			rows.add(new ShortcutRow("AI 导航", () -> preferences.navigationEnabled, v -> preferences.navigationEnabled = v, () -> preferences.navigation, v -> preferences.navigation = v, "F7"));
			rows.add(new ShortcutRow("小游戏中心", () -> preferences.minigamesEnabled, v -> preferences.minigamesEnabled = v, () -> preferences.minigames, v -> preferences.minigames = v, "F9"));
			rows.add(new ShortcutRow("小游戏：上", () -> preferences.gameUpEnabled, v -> preferences.gameUpEnabled = v, () -> preferences.gameUp, v -> preferences.gameUp = v, "W"));
			rows.add(new ShortcutRow("小游戏：下", () -> preferences.gameDownEnabled, v -> preferences.gameDownEnabled = v, () -> preferences.gameDown, v -> preferences.gameDown = v, "S"));
			rows.add(new ShortcutRow("小游戏：左", () -> preferences.gameLeftEnabled, v -> preferences.gameLeftEnabled = v, () -> preferences.gameLeft, v -> preferences.gameLeft = v, "A"));
			rows.add(new ShortcutRow("小游戏：右", () -> preferences.gameRightEnabled, v -> preferences.gameRightEnabled = v, () -> preferences.gameRight, v -> preferences.gameRight = v, "D"));
			rows.add(new ShortcutRow("小游戏：动作", () -> preferences.gameActionEnabled, v -> preferences.gameActionEnabled = v, () -> preferences.gameAction, v -> preferences.gameAction = v, "SPACE"));
			rows.add(new ShortcutRow("小游戏：暂停", () -> preferences.gamePauseEnabled, v -> preferences.gamePauseEnabled = v, () -> preferences.gamePause, v -> preferences.gamePause = v, "P"));
			rows.add(new ShortcutRow("小游戏：重新开始", () -> preferences.gameRestartEnabled, v -> preferences.gameRestartEnabled = v, () -> preferences.gameRestart, v -> preferences.gameRestart = v, "R"));
			rows.add(new ShortcutRow("小游戏：辅助动作", () -> preferences.gameSecondaryEnabled, v -> preferences.gameSecondaryEnabled = v, () -> preferences.gameSecondary, v -> preferences.gameSecondary = v, "F"));
		}
		@Override protected void init() { rebuild(); }
		private void rebuild() {
			clearWidgets(); int panel = Math.min(720, width - 24); int left = (width - panel) / 2;
			int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
			int featureWidth = panel - 315;
			for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < rows.size(); i++) {
				ShortcutRow row = rows.get(page * PAGE_SIZE + i); int y = 48 + i * 24;
				addRenderableWidget(Button.builder(Component.literal(row.name), b -> { }).bounds(left, y, featureWidth, 20).build());
				addRenderableWidget(Button.builder(Component.literal(row.enabled.get() ? "开" : "关"), b -> { row.enabledSetter.accept(!row.enabled.get()); save(); rebuild(); }).bounds(left + featureWidth + 8, y, 58, 20).build());
				String keyLabel = awaiting == row ? "请按键…" : "更改按键：" + row.key.get();
				addRenderableWidget(Button.builder(Component.literal(keyLabel), b -> { awaiting = row; rebuild(); }).bounds(left + featureWidth + 72, y, 145, 20).build());
				addRenderableWidget(Button.builder(Component.literal("重置为默认按键"), b -> { row.keySetter.accept(row.defaultKey); save(); rebuild(); }).bounds(left + featureWidth + 223, y, 92, 20).build());
			}
			if (page > 0) addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; awaiting = null; rebuild(); }).bounds(left, height - 28, 86, 20).build());
			if (page + 1 < pages) addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; awaiting = null; rebuild(); }).bounds(left + 92, height - 28, 86, 20).build());
			addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose()).bounds(left + panel - 90, height - 28, 90, 20).build());
		}
		private void save() { preferences.save(); }
		@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (awaiting != null) {
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) { awaiting = null; rebuild(); return true; }
				String name = ClientPreferences.keyName(keyCode);
				if (name != null) { awaiting.keySetter.accept(name); awaiting = null; save(); rebuild(); }
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		@Override public void onClose() { save(); if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, "功能名称　　开/关　　更改按键　　重置为默认按键", width / 2, 16, 0xFFFFFF);
			graphics.drawCenteredString(font, "小游戏中心默认 F9，避免与小地图常用的 M 冲突", width / 2, 30, 0xA8E6A3);
		}
		private record ShortcutRow(String name, java.util.function.Supplier<Boolean> enabled,
			java.util.function.Consumer<Boolean> enabledSetter, java.util.function.Supplier<String> key,
			java.util.function.Consumer<String> keySetter, String defaultKey) { }
	}

	private static final class AgentConsoleScreen extends Screen {
		private final Screen parent; private String agent = "AI_1"; private String prompt = "报告当前状态并决定下一步";
		private AgentConsoleScreen(Screen parent) { super(Component.literal("F8 AI 控制台")); this.parent = parent; }
		@Override protected void init() {
			UiInbox.consoleOpen = true;
			EditBox name = new EditBox(font, width / 2 - 220, 48, 130, 20, Component.literal("AI 名称")); name.setValue(agent); name.setResponder(value -> agent = value); addRenderableWidget(name);
			EditBox message = new EditBox(font, width / 2 - 82, 48, 230, 20, Component.literal("直接发给 AI")); message.setValue(prompt); message.setResponder(value -> prompt = value); addRenderableWidget(message);
			addRenderableWidget(Button.builder(Component.literal("发送"), b -> command("aiplayer ask " + agent + " " + prompt)).bounds(width / 2 + 156, 48, 64, 20).build());
			addRenderableWidget(Button.builder(Component.literal("刷新 AI 与位置"), b -> command("aiplayer positions")).bounds(width / 2 - 220, 78, 160, 20).build());
			addRenderableWidget(Button.builder(Component.literal("模式与提示词"), b -> minecraft.setScreen(
				new PromptAssignmentScreen(this, agent))).bounds(width / 2 - 50, 78, 160, 20).build());
			addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose()).bounds(width / 2 + 110, height - 28, 110, 20).build());
			command("aiplayer positions");
		}
		private void command(String value) { if (minecraft == null || minecraft.getConnection() == null) return; UiInbox.beginCapture(); minecraft.getConnection().sendCommand(value); }
		@Override public void onClose() { UiInbox.consoleOpen = false; if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "F8 AI 控制台 · 返回内容只显示在这里", width / 2, 16, 0xFFFFFF); int y = 112; for (String line : UiInbox.lines()) { graphics.drawString(font, line, width / 2 - 220, y, 0xA8E6A3); y += 13; } }
	}

	private static final class PromptAssignmentScreen extends Screen {
		private static final String[] MODES = {"survival", "hunter", "teammate", "pvp_coach", "idle"};
		private static final String[] PROMPTS = {"survival", "hunter", "teammate", "pvp_coach", "idle"};
		private final Screen parent;
		private String agent;
		private String target = "";
		private int modeIndex;
		private int promptIndex;
		private String status = "选择 AI 模式、目标玩家和提示词";
		private PromptAssignmentScreen(Screen parent, String agent) {
			super(Component.literal("AI 提示词分配")); this.parent = parent; this.agent = agent;
		}
		@Override protected void init() {
			EditBox name = new EditBox(font, width / 2 - 210, 55, 200, 20, Component.literal("AI 名称"));
			name.setValue(agent); name.setResponder(value -> agent = value); addRenderableWidget(name);
			EditBox player = new EditBox(font, width / 2 + 10, 55, 200, 20, Component.literal("目标玩家"));
			player.setValue(target); player.setResponder(value -> target = value); addRenderableWidget(player);
			addRenderableWidget(Button.builder(Component.literal("模式：" + localized(MODES[modeIndex])), b -> {
				modeIndex = (modeIndex + 1) % MODES.length; rebuild();
			}).bounds(width / 2 - 210, 87, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("提示词：" + localized(PROMPTS[promptIndex])), b -> {
				promptIndex = (promptIndex + 1) % PROMPTS.length; rebuild();
			}).bounds(width / 2 + 10, 87, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("应用模式并分配提示词"), b -> apply())
				.bounds(width / 2 - 210, 119, 420, 22).build());
			addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
				.bounds(width / 2 - 55, 151, 110, 20).build());
		}
		private void rebuild() { clearWidgets(); init(); }
		private void apply() {
			if (minecraft == null || minecraft.getConnection() == null || agent.isBlank()) return;
			String mode = MODES[modeIndex];
			String command = switch (mode) {
				case "survival" -> "aiplayer survival " + agent;
				case "hunter" -> "aiplayer hunt " + agent + " " + target;
				case "teammate" -> "aiplayer team " + agent + " " + target;
				case "pvp_coach" -> "aiplayer coach " + agent + " " + target;
				default -> "aiplayer idle " + agent;
			};
			UiInbox.beginCapture(); minecraft.getConnection().sendCommand(command);
			minecraft.getConnection().sendCommand("aiplayer prompt assign " + agent + " " + PROMPTS[promptIndex]);
			status = "已提交 " + agent + " · " + localized(mode) + " · " + localized(PROMPTS[promptIndex]);
		}
		private static String localized(String value) { return switch (value) { case "survival" -> "生存玩家"; case "hunter" -> "猎人"; case "teammate" -> "队友"; case "pvp_coach" -> "PVP 教练"; case "idle" -> "空闲 / 通用"; default -> value; }; }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, "AI 提示词分配", width / 2, 18, 0xFFFFFF);
			graphics.drawCenteredString(font, status, width / 2, 182, 0xA8E6A3);
		}
	}

	private static final class PromptEditorScreen extends Screen {
		private static final String[] IDS = {"survival", "hunter", "teammate", "pvp_coach", "idle"};
		private final Screen parent; private int index; private String content = ""; private String status = "选择提示词后输入新内容";
		private PromptEditorScreen(Screen parent) { super(Component.literal("AI 提示词编辑")); this.parent = parent; }
		@Override protected void init() {
			addRenderableWidget(Button.builder(Component.literal("提示词名称：" + PromptAssignmentScreen.localized(IDS[index])), b -> { index = (index + 1) % IDS.length; rebuild(); }).bounds(width / 2 - 210, 52, 420, 20).build());
			EditBox editor = new EditBox(font, width / 2 - 210, 82, 420, 20, Component.literal("提示词内容")); editor.setMaxLength(1000); editor.setValue(content); editor.setResponder(v -> content = v); addRenderableWidget(editor);
			addRenderableWidget(Button.builder(Component.literal("查看当前提示词"), b -> command("aiplayer prompt show " + IDS[index])).bounds(width / 2 - 210, 114, 205, 20).build());
			addRenderableWidget(Button.builder(Component.literal("保存提示词内容"), b -> { if (!content.isBlank()) command("aiplayer prompt set " + IDS[index] + " " + content); }).bounds(width / 2 + 5, 114, 205, 20).build());
			addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 - 55, 146, 110, 20).build());
		}
		private void rebuild() { clearWidgets(); init(); }
		private void command(String value) { if (minecraft == null || minecraft.getConnection() == null) { status = "当前没有服务器连接"; return; } UiInbox.beginCapture(); minecraft.getConnection().sendCommand(value); status = "已提交，结果会显示在管理界面"; }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "AI 提示词编辑", width / 2, 18, 0xFFFFFF); graphics.drawCenteredString(font, status, width / 2, 178, 0xA8E6A3); int y = 196; for (String line : UiInbox.lines()) { graphics.drawCenteredString(font, line, width / 2, y, 0xA8E6A3); y += 12; } }
	}

	private static final class MaidTaskScreen extends Screen {
		private final Screen parent; private String maid; private String owner = ""; private String task = "跟随所有者并协助收集附近物品"; private String status = "女仆只执行其所有者在本页提交的任务";
		private MaidTaskScreen(Screen parent, String maid) { super(Component.literal("AI 女仆")); this.parent = parent; this.maid = maid; }
		@Override protected void init() {
			EditBox name = new EditBox(font, width / 2 - 210, 48, 200, 20, Component.literal("女仆 / AI 名称")); name.setValue(maid); name.setResponder(v -> maid = v); addRenderableWidget(name);
			EditBox ownerBox = new EditBox(font, width / 2 + 10, 48, 200, 20, Component.literal("所有者玩家名")); ownerBox.setValue(owner); ownerBox.setResponder(v -> owner = v); addRenderableWidget(ownerBox);
			EditBox taskBox = new EditBox(font, width / 2 - 210, 78, 420, 20, Component.literal("所有者任务")); taskBox.setMaxLength(500); taskBox.setValue(task); taskBox.setResponder(v -> task = v); addRenderableWidget(taskBox);
			button("召唤 / 创建女仆", "aiplayer create " + maid, -210, 110); button("执行所有者任务", "aiplayer ask " + maid + " 所有者" + owner + "要求你：" + task, 5, 110);
			button("跟随所有者", "aiplayer team " + maid + " " + owner, -210, 140); button("收集并整理", "aiplayer ask " + maid + " 收集附近掉落物并整理背包", 5, 140);
			button("女仆生存模式", "aiplayer survival " + maid, -210, 170); button("查看女仆状态", "aiplayer positions", 5, 170);
			addRenderableWidget(Button.builder(Component.literal("女仆模式与提示词"), b -> minecraft.setScreen(new PromptAssignmentScreen(this, maid))).bounds(width / 2 - 210, 200, 205, 20).build());
			addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 + 5, 200, 205, 20).build());
		}
		private void button(String label, String command, int offset, int y) { addRenderableWidget(Button.builder(Component.literal(label), b -> send(command)).bounds(width / 2 + offset, y, 205, 20).build()); }
		private void send(String command) { if (minecraft == null || minecraft.getConnection() == null) { status = "当前没有服务器连接"; return; } UiInbox.beginCapture(); minecraft.getConnection().sendCommand(command); status = "任务已从女仆面板提交，结果不会写入聊天栏"; }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "AI 女仆 · 所有者任务面板", width / 2, 16, 0xFFFFFF); graphics.drawCenteredString(font, status, width / 2, 230, 0xA8E6A3); }
	}

	private static final class MinigameHubScreen extends Screen {
		private final Screen parent; private MinigameHubScreen(Screen parent) { super(Component.literal("小游戏中心")); this.parent = parent; }
		@Override protected void init() { String[] names = {"贪吃蛇", "Minecraft 俄罗斯方块", "Minecraft 方块扫雷", "2048", "AI 猜拳"}; for (int i = 0; i < names.length; i++) { int index = i; addRenderableWidget(Button.builder(Component.literal(names[i]), b -> minecraft.setScreen(new LocalMinigameScreen(this, names[index]))).bounds(width / 2 - 140, 45 + i * 28, 280, 22).build()); } addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 - 55, 195, 110, 20).build()); }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta); graphics.drawCenteredString(font, "五个本地小游戏", width / 2, 16, 0xFFFFFF); }
	}

	/** Five distinct local games for the Java 17 / Mojang-mapped 1.20.1 build. */
	private static final class LocalMinigameScreen extends Screen {
		private final Screen parent; private final String gameName;
		private int score, ticks, direction = 1, foodX = 8, foodY = 5, fallingX = 4, fallingY;
		private final List<Cell> snake = new ArrayList<>();
		private final boolean[] mines = new boolean[36], revealed = new boolean[36];
		private final int[][] tiles = new int[4][4];
		private String status = "";
		private LocalMinigameScreen(Screen parent, String gameName) { super(Component.literal(gameName)); this.parent = parent; this.gameName = gameName; }
		@Override protected void init() {
			clearWidgets();
			if (gameName.equals("贪吃蛇")) initSnake();
			else if (gameName.contains("俄罗斯方块")) initTetris();
			else if (gameName.contains("扫雷")) initMinesweeper();
			else if (gameName.equals("2048")) init2048();
			else initRps();
			addRenderableWidget(Button.builder(Component.literal("返回小游戏中心"), b -> onClose())
				.bounds(width / 2 - 70, height - 28, 140, 20).build());
		}
		private void initSnake() {
			if (snake.isEmpty()) { snake.add(new Cell(5, 5)); snake.add(new Cell(4, 5)); snake.add(new Cell(3, 5)); }
			int y = height - 55; addRenderableWidget(Button.builder(Component.literal("←"), b -> turn(2)).bounds(width / 2 - 76, y, 36, 20).build());
			addRenderableWidget(Button.builder(Component.literal("↑"), b -> turn(3)).bounds(width / 2 - 38, y, 36, 20).build());
			addRenderableWidget(Button.builder(Component.literal("↓"), b -> turn(4)).bounds(width / 2, y, 36, 20).build());
			addRenderableWidget(Button.builder(Component.literal("→"), b -> turn(1)).bounds(width / 2 + 38, y, 36, 20).build());
			status = "方向键或已配置的上下左右键控制；吃到绿色食物增长";
		}
		private void turn(int next) { if ((direction == 1 && next != 2) || (direction == 2 && next != 1) || (direction == 3 && next != 4) || (direction == 4 && next != 3)) direction = next; }
		private void initTetris() {
			addRenderableWidget(Button.builder(Component.literal("左移"), b -> fallingX = Math.max(0, fallingX - 1)).bounds(width / 2 - 130, height - 55, 80, 20).build());
			addRenderableWidget(Button.builder(Component.literal("快速下落"), b -> { fallingY = 15; settleTetris(); }).bounds(width / 2 - 40, height - 55, 80, 20).build());
			addRenderableWidget(Button.builder(Component.literal("右移"), b -> fallingX = Math.min(8, fallingX + 1)).bounds(width / 2 + 50, height - 55, 80, 20).build());
			status = "2×2 方块会自动下落；左右移动并完成落点，分数随落块增加";
		}
		private void initMinesweeper() {
			if (!mines[0] && !mines[5] && !mines[35]) { int[] seed = {2, 8, 15, 21, 29, 34}; for (int value : seed) mines[value] = true; }
			int size = 24, left = width / 2 - size * 3;
			for (int i = 0; i < 36; i++) { int index = i; String label = revealed[i] ? (mines[i] ? "✹" : Integer.toString(neighbors(i))) : "■";
				Button button = Button.builder(Component.literal(label), b -> reveal(index)).bounds(left + i % 6 * size, 48 + i / 6 * size, size - 2, size - 2).build(); button.active = !revealed[i]; addRenderableWidget(button); }
			status = "6×6 方块扫雷：6 枚雷；数字表示相邻地雷数量";
		}
		private int neighbors(int index) { int x = index % 6, y = index / 6, count = 0; for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) { int nx = x + dx, ny = y + dy; if (nx >= 0 && nx < 6 && ny >= 0 && ny < 6 && mines[ny * 6 + nx]) count++; } return count; }
		private void reveal(int index) { revealed[index] = true; if (mines[index]) { status = "踩雷了，分数清零；点击任意未翻方块继续练习"; score = 0; } else { score++; status = "安全方块 +1；当前已发现 " + score + " 个"; } init(); }
		private void init2048() {
			if (emptyTiles() == 16) { addTile(); addTile(); }
			int size = 36, left = width / 2 - size * 2;
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) { Button tile = Button.builder(Component.literal(tiles[y][x] == 0 ? "·" : Integer.toString(tiles[y][x])), b -> { }).bounds(left + x * size, 44 + y * size, size - 2, size - 2).build(); tile.active = false; addRenderableWidget(tile); }
			addRenderableWidget(Button.builder(Component.literal("←"), b -> move2048(0)).bounds(width / 2 - 76, 196, 36, 20).build()); addRenderableWidget(Button.builder(Component.literal("↑"), b -> move2048(1)).bounds(width / 2 - 38, 196, 36, 20).build()); addRenderableWidget(Button.builder(Component.literal("↓"), b -> move2048(2)).bounds(width / 2, 196, 36, 20).build()); addRenderableWidget(Button.builder(Component.literal("→"), b -> move2048(3)).bounds(width / 2 + 38, 196, 36, 20).build());
			status = "合并相同数字，目标 2048；支持方向键和已配置按键";
		}
		private int emptyTiles() { int count = 0; for (int[] row : tiles) for (int value : row) if (value == 0) count++; return count; }
		private void addTile() { int empty = emptyTiles(); if (empty == 0) return; int target = (int) (Math.random() * empty); for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (tiles[y][x] == 0 && target-- == 0) { tiles[y][x] = Math.random() < .9 ? 2 : 4; return; } }
		private void move2048(int direction) { boolean changed = false; for (int line = 0; line < 4; line++) { int[] raw = new int[4]; for (int i = 0; i < 4; i++) raw[i] = get2048(direction, line, i); int[] merged = mergeLine(raw); for (int i = 0; i < 4; i++) { if (get2048(direction, line, i) != merged[i]) changed = true; set2048(direction, line, i, merged[i]); } } if (changed) addTile(); init(); }
		private int[] mergeLine(int[] raw) { int[] compact = new int[4]; int n = 0; for (int value : raw) if (value != 0) compact[n++] = value; for (int i = 0; i < 3; i++) if (compact[i] != 0 && compact[i] == compact[i + 1]) { compact[i] *= 2; score += compact[i]; for (int j = i + 1; j < 3; j++) compact[j] = compact[j + 1]; compact[3] = 0; } return compact; }
		private int get2048(int direction, int line, int i) { return switch (direction) { case 0 -> tiles[line][i]; case 1 -> tiles[i][line]; case 2 -> tiles[3 - i][line]; default -> tiles[line][3 - i]; }; }
		private void set2048(int direction, int line, int i, int value) { switch (direction) { case 0 -> tiles[line][i] = value; case 1 -> tiles[i][line] = value; case 2 -> tiles[3 - i][line] = value; default -> tiles[line][3 - i] = value; } }
		private void initRps() { addRenderableWidget(Button.builder(Component.literal("石头"), b -> rps(0)).bounds(width / 2 - 155, 72, 100, 24).build()); addRenderableWidget(Button.builder(Component.literal("剪刀"), b -> rps(1)).bounds(width / 2 - 50, 72, 100, 24).build()); addRenderableWidget(Button.builder(Component.literal("布"), b -> rps(2)).bounds(width / 2 + 55, 72, 100, 24).build()); if (status.isBlank()) status = "选择出拳，AI 会独立随机出拳"; }
		private void rps(int mine) { int ai = (int) (Math.random() * 3); String[] names = {"石头", "剪刀", "布"}; int result = mine == ai ? 0 : ((mine == 0 && ai == 1) || (mine == 1 && ai == 2) || (mine == 2 && ai == 0) ? 1 : -1); if (result > 0) score++; status = "你出" + names[mine] + "，AI 出" + names[ai] + "：" + (result > 0 ? "你赢了" : result < 0 ? "AI 获胜" : "平局"); }
		@Override public void tick() { if (++ticks % 6 != 0) return; if (gameName.equals("贪吃蛇")) stepSnake(); else if (gameName.contains("俄罗斯方块")) { fallingY++; if (fallingY >= 15) settleTetris(); } }
		private void stepSnake() { Cell head = snake.get(0); int nx = head.x + (direction == 1 ? 1 : direction == 2 ? -1 : 0), ny = head.y + (direction == 4 ? 1 : direction == 3 ? -1 : 0); Cell next = new Cell(nx, ny); if (nx < 0 || nx >= 16 || ny < 0 || ny >= 10 || snake.contains(next)) { snake.clear(); score = 0; status = "撞到了边界或自己，已重新开始"; init(); return; } snake.add(0, next); if (nx == foodX && ny == foodY) { score++; foodX = (int) (Math.random() * 16); foodY = (int) (Math.random() * 10); } else snake.remove(snake.size() - 1); }
		private void settleTetris() { score++; fallingY = 0; fallingX = 1 + (int) (Math.random() * 7); status = "已放置 " + score + " 个方块；继续调整下一块落点"; }
		@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { if (gameName.equals("贪吃蛇")) { if (preferences.gameLeftEnabled && keyCode == preferences.code(preferences.gameLeft, "A")) turn(2); else if (preferences.gameRightEnabled && keyCode == preferences.code(preferences.gameRight, "D")) turn(1); else if (preferences.gameUpEnabled && keyCode == preferences.code(preferences.gameUp, "W")) turn(3); else if (preferences.gameDownEnabled && keyCode == preferences.code(preferences.gameDown, "S")) turn(4); else return super.keyPressed(keyCode, scanCode, modifiers); return true; } if (gameName.equals("2048")) { if (keyCode == preferences.code(preferences.gameLeft, "A")) move2048(0); else if (keyCode == preferences.code(preferences.gameUp, "W")) move2048(1); else if (keyCode == preferences.code(preferences.gameDown, "S")) move2048(2); else if (keyCode == preferences.code(preferences.gameRight, "D")) move2048(3); else return super.keyPressed(keyCode, scanCode, modifiers); return true; } return super.keyPressed(keyCode, scanCode, modifiers); }
		@Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
		@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			renderBackground(graphics); super.render(graphics, mouseX, mouseY, delta);
			graphics.drawCenteredString(font, gameName + " · 得分 " + score, width / 2, 16, 0xFFFFFF);
			graphics.drawCenteredString(font, status, width / 2, 30, 0xA8E6A3);
			if (gameName.equals("贪吃蛇")) { int left = width / 2 - 128; for (Cell cell : snake) graphics.fill(left + cell.x * 16, 48 + cell.y * 16, left + cell.x * 16 + 14, 48 + cell.y * 16 + 14, 0xFF66CC66); graphics.fill(left + foodX * 16, 48 + foodY * 16, left + foodX * 16 + 14, 48 + foodY * 16 + 14, 0xFFFF5555); }
			if (gameName.contains("俄罗斯方块")) { int left = width / 2 - 80; graphics.fill(left, 44, left + 160, 204, 0x55222222); graphics.fill(left + fallingX * 16, 44 + fallingY * 10, left + fallingX * 16 + 30, 44 + fallingY * 10 + 18, 0xFF55AAFF); }
		}
		private record Cell(int x, int y) { }
	}

	private static final class UiInbox {
		private static final List<String> LINES = new ArrayList<>(); private static long until; private static long revision; private static boolean consoleOpen;
		static void beginCapture() { until = System.currentTimeMillis() + 8000; }
		static boolean capture(Component message) { if (!consoleOpen && System.currentTimeMillis() > until) return false; String value = message.getString(); if (value.isBlank()) return false; LINES.add(value); if (LINES.size() > 12) LINES.remove(0); revision++; return true; }
		static long revision() { return revision; } static String latest() { return LINES.isEmpty() ? "" : LINES.get(LINES.size() - 1); } static List<String> lines() { return List.copyOf(LINES); }
	}

	private static final class ClientPreferences {
		private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
		private static final Path FILE = Path.of("config", "windowsdepcs-ai-companion-1.20.1-client.json");
		int shortcutDefaultsVersion;
		boolean uiPrimaryEnabled = true, uiSecondaryEnabled = true, positionsEnabled = true, zoomEnabled = true,
			navigationEnabled = true, minigamesEnabled = true;
		boolean gameUpEnabled = true, gameDownEnabled = true, gameLeftEnabled = true, gameRightEnabled = true,
			gameActionEnabled = true, gamePauseEnabled = true, gameRestartEnabled = true, gameSecondaryEnabled = true;
		String uiPrimary = "V", uiSecondary = "B", positions = "F8", zoom = "C", navigation = "F7", minigames = "F9";
		String gameUp = "W", gameDown = "S", gameLeft = "A", gameRight = "D", gameAction = "SPACE",
			gamePause = "P", gameRestart = "R", gameSecondary = "F";
		String apiEndpoint = "https://api.openai.com/v1", apiModel = "gpt-5-mini";
		static ClientPreferences load() { try { if (Files.isRegularFile(FILE)) { ClientPreferences value = GSON.fromJson(Files.readString(FILE), ClientPreferences.class); if (value != null) return value; } } catch (Exception ignored) { } return new ClientPreferences(); }
		void migrateLegacyDefaults() { if (shortcutDefaultsVersion >= 3) return; if ("M".equalsIgnoreCase(minigames)) minigames = "F9"; if ("F6".equalsIgnoreCase(zoom)) zoom = "C"; if ("G".equalsIgnoreCase(navigation)) navigation = "F7"; uiPrimaryEnabled = uiSecondaryEnabled = positionsEnabled = zoomEnabled = navigationEnabled = minigamesEnabled = true; gameUpEnabled = gameDownEnabled = gameLeftEnabled = gameRightEnabled = gameActionEnabled = gamePauseEnabled = gameRestartEnabled = gameSecondaryEnabled = true; shortcutDefaultsVersion = 3; save(); }
		void save() { normalize(); try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8); } catch (Exception error) { throw new IllegalStateException("客户端设置保存失败", error); } }
		void normalize() { uiPrimary = gameplay(uiPrimary, "V"); uiSecondary = gameplay(uiSecondary, "B"); positions = gameplay(positions, "F8"); zoom = gameplay(zoom, "C"); navigation = gameplay(navigation, "F7"); minigames = gameplay(minigames, "F9"); gameUp = gameplay(gameUp, "W"); gameDown = gameplay(gameDown, "S"); gameLeft = gameplay(gameLeft, "A"); gameRight = gameplay(gameRight, "D"); gameAction = gameplay(gameAction, "SPACE"); gamePause = gameplay(gamePause, "P"); gameRestart = gameplay(gameRestart, "R"); gameSecondary = gameplay(gameSecondary, "F"); }
		int code(String value, String fallback) { value = value == null ? fallback : value.strip().toUpperCase(); if (value.equals("SPACE")) return GLFW.GLFW_KEY_SPACE; if (value.matches("F([1-9]|1[0-2])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(value.substring(1)) - 1; if (value.matches("[0-9]")) return value.charAt(0); return letter(value, fallback).charAt(0); }
		static String keyName(int keyCode) { if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) return Character.toString((char) keyCode); if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) return Character.toString((char) keyCode); if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1); if (keyCode == GLFW.GLFW_KEY_SPACE) return "SPACE"; return null; }
		private static String letter(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.matches("[A-Z]") ? v : fallback; }
		private static String function(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.matches("F([1-9]|1[0-2])") ? v : fallback; }
		private static String gameplay(String value, String fallback) { String v = value == null ? "" : value.strip().toUpperCase(); return v.equals("SPACE") || v.matches("[A-Z0-9]") || v.matches("F([1-9]|1[0-2])") ? v : fallback; }
	}
}
