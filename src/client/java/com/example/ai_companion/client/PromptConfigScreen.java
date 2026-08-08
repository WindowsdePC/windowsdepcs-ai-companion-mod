package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.client.minigame.Game2048Screen;
import com.example.ai_companion.client.minigame.MinigameCenterScreen;
import com.example.ai_companion.client.minigame.MinigameProgress;
import com.example.ai_companion.client.minigame.MinesweeperScreen;
import com.example.ai_companion.client.minigame.RockPaperScissorsScreen;
import com.example.ai_companion.client.minigame.SnakeScreen;
import com.example.ai_companion.client.minigame.TetrisScreen;
import com.example.ai_companion.config.PromptStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Unified in-game configuration UI with AI, prompt and optional-feature sections. */
public final class PromptConfigScreen extends Screen {
	private enum Tab {
		AI_SYSTEM("AI系统"),
		SHORTCUTS("快捷键修改"),
		GAMEPLAY("游戏增强"),
		CLIENT("客户端增强"),
		MINIGAMES("小游戏中心"),
		LEISURE("休闲系统"),
		PERFORMANCE("性能优化"),
		COMPATIBILITY("兼容设置"),
		ADVANCED("高级设置");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private enum AiSection { MANAGEMENT, MAID, API, PROMPTS }

	private final PromptStore promptStore;
	private final ClientSettings settings;
	private final UiBackend backend;
	private final Screen parent;
	private final MinigameProgress minigameProgress;
	private Tab tab = Tab.AI_SYSTEM;
	private AiSection aiSection = AiSection.MANAGEMENT;
	private String status = "修改服务器设置和分配 AI 需要管理员权限";
	private long positionRevision = -1;
	private long uiResultRevision = UiActionClient.revision();
	private long apiConfigRevision = UiActionClient.configRevision();
	private boolean apiConfigRequested;

	private String baseName = "AI_";
	private String createCount = "2";
	private String agentName = "";
	private String selectedPlayer = "";
	private String instruction = "根据当前模式和观察决定下一步";
	private AgentMode selectedMode = AgentMode.HUNTER;
	private boolean playersExpanded;

	private List<String> promptIds = new ArrayList<>();
	private int promptIndex;
	private String promptId = "idle";
	private String promptText = "";

	private boolean rushEnabled;
	private boolean flexibleEquipmentEnabled;
	private boolean sprintJumpEnabled;
	private boolean clothNavigationTop;
	private boolean spyglassHighlightEnabled;
	private String spyglassRadiusChunks;
	private String spyglassHoldSeconds;
	private String spyglassDurationSeconds;
	private String spyglassCooldownSeconds;
	private String spyglassMaxTargets;
	private com.example.ai_companion.spyglass.SpyglassTargetCondition spyglassTargetCondition;
	private boolean screenZoomEnabled;
	private String zoomKey;
	private String zoomFactor;
	private String zoomTransitionSeconds;
	private boolean performanceOptimizerEnabled;
	private boolean adaptiveExtraRenderDistance;
	private String performanceTargetFps;
	private String extraRenderDistance;
	private String minimumExtraRenderDistance;
	private boolean worldNavigatorEnabled;
	private String navigatorKey;
	private boolean mercifulVoidEnabled;
	private boolean maximumWorldBorderEnabled;
	private String durabilityEvery;
	private String hungerEvery;
	private String hungerCost;
	private String rushStrength;
	private String primaryKey;
	private String secondaryKey;
	private String positionsKey;
	private String minigameKey;
	private String apiBase;
	private String apiModel;
	private String apiToken = "";

	private EditBox idBox;
	private EditBox agentBox;
	private MultiLineEditBox promptBox;

	public PromptConfigScreen(PromptStore promptStore, ClientSettings settings, UiBackend backend) {
		this(promptStore, settings, backend, null);
	}

	public PromptConfigScreen(PromptStore promptStore, ClientSettings settings, UiBackend backend, Screen parent) {
		super(Component.literal("WindowsdePC's AI Companion Mod · 统一设置"));
		this.promptStore = promptStore;
		this.settings = settings;
		this.backend = backend;
		this.parent = parent;
		this.minigameProgress = MinigameProgress.load();
		rushEnabled = settings.goldenSpearRushEnabled;
		flexibleEquipmentEnabled = settings.flexibleEquipmentEnabled;
		sprintJumpEnabled = settings.sprintJumpEnabled;
		clothNavigationTop = settings.clothNavigationTop;
		spyglassHighlightEnabled = settings.spyglassHighlightEnabled;
		spyglassRadiusChunks = Integer.toString(settings.spyglassRadiusChunks);
		spyglassHoldSeconds = Integer.toString(settings.spyglassHoldSeconds);
		spyglassDurationSeconds = Integer.toString(settings.spyglassDurationSeconds);
		spyglassCooldownSeconds = Integer.toString(settings.spyglassCooldownSeconds);
		spyglassMaxTargets = Integer.toString(settings.spyglassMaxTargets);
		spyglassTargetCondition = com.example.ai_companion.spyglass.SpyglassTargetCondition.parse(settings.spyglassTargetCondition);
		screenZoomEnabled = settings.screenZoomEnabled;
		zoomKey = settings.zoomKey;
		zoomFactor = Double.toString(settings.zoomFactor);
		zoomTransitionSeconds = Double.toString(settings.zoomTransitionSeconds);
		performanceOptimizerEnabled = settings.clientPerformanceOptimizerEnabled;
		adaptiveExtraRenderDistance = settings.adaptiveExtraRenderDistance;
		performanceTargetFps = Integer.toString(settings.performanceTargetFps);
		extraRenderDistance = Integer.toString(settings.extraRenderDistance);
		minimumExtraRenderDistance = Integer.toString(settings.minimumExtraRenderDistance);
		worldNavigatorEnabled = settings.worldNavigatorEnabled;
		navigatorKey = settings.navigatorKey;
		mercifulVoidEnabled = settings.mercifulVoidEnabled;
		maximumWorldBorderEnabled = settings.maximumWorldBorderEnabled;
		durabilityEvery = Integer.toString(settings.durabilityEvery);
		hungerEvery = Integer.toString(settings.hungerEvery);
		hungerCost = Integer.toString(settings.hungerCost);
		rushStrength = Double.toString(settings.rushStrength);
		primaryKey = settings.primaryKey;
		secondaryKey = settings.secondaryKey;
		positionsKey = settings.positionsKey;
		minigameKey = settings.minigameKey;
		apiBase = settings.apiBase;
		apiModel = settings.model;
		selectedMode = settings.defaultAgentMode();
		reloadPromptIds();
		if (!promptIds.isEmpty()) loadPrompt(promptIds.getFirst());
	}

	@Override
	public void onClose() {
		if (minecraft != null && parent != null) minecraft.setScreenAndShow(parent);
		else super.onClose();
	}

	@Override
	protected void init() {
		rebuildPanel();
	}

	@Override
	public void tick() {
		super.tick();
		if (uiResultRevision != UiActionClient.revision()) {
			uiResultRevision = UiActionClient.revision();
			status = UiActionClient.lastMessage();
		}
		if (apiConfigRevision != UiActionClient.configRevision()) {
			apiConfigRevision = UiActionClient.configRevision();
			apiBase = UiActionClient.serverApiBase();
			apiModel = UiActionClient.serverModel();
			settings.apiBase = apiBase;
			settings.model = apiModel;
			try { settings.save(); }
			catch (IOException error) { status = "服务器配置已读取，但客户端镜像保存失败: " + error.getMessage(); }
			if (tab == Tab.AI_SYSTEM && aiSection == AiSection.API) rebuildPanel();
		}
		if (tab == Tab.AI_SYSTEM && aiSection == AiSection.MANAGEMENT
				&& positionRevision != AgentPositionHud.revision()) {
			positionRevision = AgentPositionHud.revision();
			rebuildPanel();
		}
	}

	private void rebuildPanel() {
		clearWidgets();
		int fullWidth = Math.min(920, width - 24);
		int left = (width - fullWidth) / 2;
		boolean topNavigation = backend == UiBackend.CLOTH_CONFIG && clothNavigationTop;
		int panelLeft = topNavigation ? left : left + 158;
		int panelWidth = topNavigation ? fullWidth : fullWidth - 158;
		if (topNavigation) {
			int tabWidth = Math.max(72, (fullWidth - 24) / Tab.values().length); int x = left;
			for (Tab candidate : Tab.values()) { addRenderableWidget(Button.builder(Component.literal((candidate == tab ? "●" : "") + candidate.label), button -> switchTab(candidate)).bounds(x, 4, tabWidth, 20).build()); x += tabWidth + 3; }
		} else {
			int y = 28; for (Tab candidate : Tab.values()) { String marker = candidate == tab ? "▶ " : "  "; addRenderableWidget(Button.builder(Component.literal(marker + candidate.label), button -> switchTab(candidate)).bounds(left, y, 148, 20).build()); y += 23; }
		}

		switch (tab) {
			case AI_SYSTEM -> buildAiSystemPanel(panelLeft, panelWidth);
			case SHORTCUTS -> buildShortcutPanel(panelLeft, panelWidth);
			case GAMEPLAY -> buildGameplayPanel(panelLeft, panelWidth);
			case CLIENT -> buildClientPanel(panelLeft, panelWidth);
			case MINIGAMES -> buildMinigamePanel(panelLeft, panelWidth);
			case LEISURE -> buildLeisurePanel(panelLeft, panelWidth);
			case PERFORMANCE -> buildPerformancePanel(panelLeft, panelWidth);
			case COMPATIBILITY -> buildCompatibilityPanel(panelLeft, panelWidth);
			case ADVANCED -> buildAdvancedPanel(panelLeft, panelWidth);
		}
		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
			.bounds(left + fullWidth - 90, height - 25, 90, 20).build());
	}

	private void buildAiSystemPanel(int left, int panelWidth) {
		int sectionWidth = (panelWidth - 12) / 4;
		addRenderableWidget(Button.builder(Component.literal("AI 管理"), b -> switchAiSection(AiSection.MANAGEMENT))
			.bounds(left, 28, sectionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("AI 女仆"), b -> switchAiSection(AiSection.MAID))
			.bounds(left + sectionWidth + 4, 28, sectionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("API"), b -> switchAiSection(AiSection.API))
			.bounds(left + (sectionWidth + 4) * 2, 28, sectionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("提示词"), b -> switchAiSection(AiSection.PROMPTS))
			.bounds(left + (sectionWidth + 4) * 3, 28, sectionWidth, 20).build());
		switch (aiSection) {
			case MANAGEMENT -> buildAiPanel(left, panelWidth);
			case MAID -> buildMaidPanel(left, panelWidth);
			case API -> buildApiPanel(left, panelWidth);
			case PROMPTS -> buildPromptPanel(left, panelWidth);
		}
	}

	private void buildMaidPanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("打开 AI 女仆创建与聊天界面"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(
				new com.example.ai_companion.client.maid.MaidScreen(this));
		}).bounds(left, 76, Math.min(330, panelWidth), 22).build());
		status = "可命名、选择 7 个默认皮肤、导入本地皮肤/披风，并通过文字向女仆发送 AI 指令";
	}

	private void switchAiSection(AiSection next) {
		capturePromptDraft();
		aiSection = next;
		playersExpanded = false;
		if (next == AiSection.API) requestApiConfig();
		rebuildPanel();
	}

	private void buildPlaceholderPanel(String message) {
		status = message;
	}

	private void buildMinigamePanel(int left, int panelWidth) {
		int cardWidth = (panelWidth - 14) / 2;
		addRenderableWidget(Button.builder(Component.literal("打开小游戏弹窗（5 个纯本地游戏）"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new MinigameCenterScreen(this, minigameProgress));
		}).bounds(left, 62, panelWidth, 24).build());
		addRenderableWidget(Button.builder(Component.literal("打开 AI 宠物竞技"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new PetCompetitionScreen(this));
		}).bounds(left, 102, cardWidth, 22).build());
		addRenderableWidget(Button.builder(Component.literal("打开 AI 竞技场"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new AiArenaScreen(this));
		}).bounds(left + cardWidth + 14, 102, cardWidth, 22).build());
		addRenderableWidget(Button.builder(Component.literal("我的竞技宠物"), button ->
			UiActionClient.send("pet.list")).bounds(left, 140, cardWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("竞技宠物排行榜"), button ->
			UiActionClient.send("pet.leaderboard")).bounds(left + cardWidth + 14, 140,
			cardWidth, 20).build());
		status = "小游戏会在独立弹窗中纯本地运行；AI 宠物与 AI 竞技通过服务端 UI 通道运行";
	}

	private void buildLeisurePanel(int left, int panelWidth) {
		int cardWidth = (panelWidth - 14) / 2;
		directButton("打开相册列表", "album.list", left, 60, cardWidth);
		directButton("旅行日志统计", "travel.stats", left + cardWidth + 14, 60, cardWidth);
		directButton("生成今日 Minecraft 日报", "news.today", left, 88, cardWidth);
		directButton("查看 AI 直播状态", "live.status", left + cardWidth + 14, 88, cardWidth);
		directButton("查看 AI 合奏状态", "music.status", left, 116, cardWidth);
		directButton("模拟社会排行榜", "society.leaderboard", left + cardWidth + 14, 116, cardWidth);
		directButton("自然事件状态", "weather.status", left, 144, cardWidth);
		directButton("AI 助手球探索", "orb.explore", left + cardWidth + 14, 144, cardWidth);
		status = "休闲查询直接调用服务端 Java 管理器，不再向聊天栏发送命令";
	}

	private void buildCompatibilityPanel(int left, int panelWidth) {
		int cardWidth = (panelWidth - 14) / 2;
		if (backend == UiBackend.ECLIPSE_UI) {
			addRenderableWidget(Button.builder(Component.literal("打开 EclipseUI 现代化数值设置"), b -> {
				if (minecraft != null) minecraft.setScreenAndShow(backend.createScreen(this, settings));
			}).bounds(left, 42, panelWidth, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("检查 API 配置状态"), b ->
			UiActionClient.send("config.status")).bounds(left, 70, cardWidth, 20).build());
		directButton("检查游戏增强状态", "feature.status", left + cardWidth + 14, 70, cardWidth);
		directButton("检查自然事件配置", "weather.config", left, 98, cardWidth);
		addRenderableWidget(Button.builder(Component.literal("刷新 AI 位置"), button -> {
			if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
			status = "正在从服务器刷新 AI 位置";
		}).bounds(left + cardWidth + 14, 98, cardWidth, 20).build());
		status = "当前 UI 后端：" + backend.displayName() + "；Simple Voice Chat 与背包兼容均为可选";
	}

	private void directButton(String label, String action, int x, int y, int width) {
		addRenderableWidget(Button.builder(Component.literal(label), button -> {
			UiActionClient.send(action);
			status = "已直接请求服务器：" + label;
		}).bounds(x, y, width, 20).build());
	}

	private void buildApiPanel(int left, int panelWidth) {
		requestApiConfig();
		EditBox endpoint = addRenderableWidget(new EditBox(font, left, 75, panelWidth, 20,
			Component.literal("API 地址")));
		endpoint.setMaxLength(300);
		endpoint.setValue(apiBase);
		endpoint.setResponder(value -> apiBase = value);

		EditBox model = addRenderableWidget(new EditBox(font, left, 125, panelWidth, 20,
			Component.literal("模型")));
		model.setMaxLength(100);
		model.setValue(apiModel);
		model.setResponder(value -> apiModel = value);

		EditBox token = addRenderableWidget(new EditBox(font, left, 175, panelWidth, 20,
			Component.literal("API 令牌")));
		token.setMaxLength(500);
		token.setValue(apiToken);
		token.setResponder(value -> apiToken = value);

		addRenderableWidget(Button.builder(Component.literal("保存 API 配置"), b -> saveApi())
			.bounds(left, 220, 180, 20).build());
		addRenderableWidget(Button.builder(Component.literal("查询服务器状态"), b ->
			UiActionClient.send("config.status")).bounds(left + 190, 220, 180, 20).build());
	}

	private void switchTab(Tab next) {
		capturePromptDraft();
		tab = next;
		playersExpanded = false;
		rebuildPanel();
	}

	private void buildAiPanel(int left, int panelWidth) {
		if (minecraft != null && positionRevision < 0) {
			positionRevision = AgentPositionHud.revision();
			AgentPositionHud.requestRefresh(minecraft);
		}
		EditBox base = addRenderableWidget(new EditBox(font, left, 70, 180, 20,
			Component.literal("AI 名称前缀")));
		base.setMaxLength(13);
		base.setValue(baseName);
		base.setResponder(value -> baseName = value);

		EditBox count = addRenderableWidget(new EditBox(font, left + 190, 70, 70, 20,
			Component.literal("数量")));
		count.setMaxLength(2);
		count.setValue(createCount);
		count.setResponder(value -> createCount = value);
		addRenderableWidget(Button.builder(Component.literal("批量生成"), b -> createMany())
			.bounds(left + 270, 70, 110, 20).build());

		EditBox agent = addRenderableWidget(new EditBox(font, left, 120, 180, 20,
			Component.literal("AI 名称")));
		agent.setMaxLength(16);
		agent.setValue(agentName);
		agent.setResponder(value -> agentName = value);

		addRenderableWidget(Button.builder(Component.literal("模式：" + modeLabel()), b -> cycleMode())
			.bounds(left + 190, 120, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal(selectedPlayer.isBlank()
				? "选择当前玩家 ▼" : "目标：" + selectedPlayer + " ▼"), b -> togglePlayers())
			.bounds(left + 350, 120, 190, 20).build());
		addRenderableWidget(Button.builder(Component.literal("提示词：" + promptId), b -> cyclePrompt())
			.bounds(left + 550, 120, panelWidth - 550, 20).build());

		if (playersExpanded) {
			List<String> players = onlinePlayers();
			int y = 145;
			for (String player : players.stream().limit(8).toList()) {
				addRenderableWidget(Button.builder(Component.literal(player), b -> choosePlayer(player))
					.bounds(left + 350, y, 190, 20).build());
				y += 22;
			}
			if (players.isEmpty()) status = "当前连接中没有可选择的玩家";
		}

		addRenderableWidget(Button.builder(Component.literal("应用模式与提示词"), b -> applyAgentSetup())
			.bounds(left, 155, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("设为空闲"), b -> setIdle())
			.bounds(left + 230, 155, 110, 20).build());

		EditBox ask = addRenderableWidget(new EditBox(font, left, 205, panelWidth - 120, 20,
			Component.literal("给 AI 的任务")));
		ask.setMaxLength(500);
		ask.setValue(instruction);
		ask.setResponder(value -> instruction = value);
		addRenderableWidget(Button.builder(Component.literal("让 AI 思考"), b -> askAgent())
			.bounds(left + panelWidth - 110, 205, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("打开 AI 竞技场"), b -> {
			if (minecraft != null) minecraft.setScreenAndShow(new AiArenaScreen(this));
		}).bounds(left, 240, (panelWidth - 10) / 2, 22).build());
		addRenderableWidget(Button.builder(Component.literal("打开 AI 宠物竞技"), b -> {
			if (minecraft != null) minecraft.setScreenAndShow(new PetCompetitionScreen(this));
		}).bounds(left + (panelWidth + 10) / 2, 240, (panelWidth - 10) / 2, 22).build());

		addRenderableWidget(Button.builder(Component.literal("刷新 AI 列表"), b -> {
			if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
			status = "正在从服务器刷新 AI 当前维度与位置…";
		}).bounds(left, 270, 140, 20).build());
		List<com.example.ai_companion.agent.AgentPosition> liveAgents = AgentPositionHud.snapshot();
		if (liveAgents.isEmpty()) {
			status = "AI 列表暂无数据；点击“刷新 AI 列表”从服务器获取";
			return;
		}
		int y = 295;
		for (var position : liveAgents.stream().limit(5).toList()) {
			int rowWidth = Math.max(220, panelWidth - 150);
			addRenderableWidget(Button.builder(Component.literal(position.displayText()), b -> {
				agentName = position.name();
				status = "已选择 " + position.name() + "；当前维度 " + position.dimension();
			}).bounds(left, y, rowWidth, 20).build());
			addRenderableWidget(Button.builder(Component.literal("传送至 " + position.name()), b -> {
				UiActionClient.send("agent.teleport_to", position.name());
				status = "已请求服务器传送至 " + position.name() + "（需要管理员权限）";
			}).bounds(left + rowWidth + 8, y, Math.max(130, panelWidth - rowWidth - 8), 20).build());
			y += 22;
		}
	}

	private void buildPromptPanel(int left, int panelWidth) {
		idBox = addRenderableWidget(new EditBox(font, left, 67, panelWidth / 2 - 5, 20,
			Component.literal("提示词 ID")));
		idBox.setMaxLength(PromptStore.MAX_ID_LENGTH);
		idBox.setValue(promptId);
		idBox.setResponder(value -> promptId = value);

		agentBox = addRenderableWidget(new EditBox(font, left + panelWidth / 2 + 5, 67,
			panelWidth / 2 - 5, 20, Component.literal("分配给 AI")));
		agentBox.setMaxLength(16);
		agentBox.setValue(agentName);
		agentBox.setResponder(value -> agentName = value);

		int editorHeight = Math.max(90, height - 190);
		promptBox = addRenderableWidget(MultiLineEditBox.builder().setX(left).setY(94)
			.setPlaceholder(Component.literal("完整系统提示词；{targets} 会替换为所选玩家"))
			.build(font, panelWidth, editorHeight, Component.literal("提示词正文")));
		promptBox.setCharacterLimit(PromptStore.MAX_PROMPT_LENGTH);
		promptBox.setLineLimit(240);
		promptBox.setValue(promptText);
		promptBox.setValueListener(value -> promptText = value);

		int y = 101 + editorHeight;
		int gap = 4;
		int buttonWidth = (panelWidth - gap * 6) / 7;
		addRenderableWidget(Button.builder(Component.literal("上一个"), b -> selectPrompt(-1))
			.bounds(left, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("下一个"), b -> selectPrompt(1))
			.bounds(left + (buttonWidth + gap), y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("新建"), b -> newPrompt())
			.bounds(left + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("保存"), b -> savePrompt())
			.bounds(left + (buttonWidth + gap) * 3, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("删除"), b -> deletePrompt())
			.bounds(left + (buttonWidth + gap) * 4, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("恢复"), b -> resetPrompt())
			.bounds(left + (buttonWidth + gap) * 5, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("分配"), b -> assignPrompt())
			.bounds(left + (buttonWidth + gap) * 6, y, buttonWidth, 20).build());
	}

	private void buildGameplayPanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("金矛二级突进：" + (rushEnabled ? "开启" : "关闭")),
			b -> {
				rushEnabled = !rushEnabled;
				rebuildPanel();
			}).bounds(left, 70, 220, 20).build());

		addRenderableWidget(Button.builder(Component.literal("任意物品装备："
			+ (flexibleEquipmentEnabled ? "开启" : "关闭")), b -> {
			flexibleEquipmentEnabled = !flexibleEquipmentEnabled;
			rebuildPanel();
		}).bounds(left + 230, 70, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("持续疾跑跳跃：" + (sprintJumpEnabled ? "开启" : "关闭")), b -> { sprintJumpEnabled = !sprintJumpEnabled; rebuildPanel(); })
			.bounds(left + 460, 70, 180, 20).build());
		addRenderableWidget(Button.builder(Component.literal("打乱非快捷栏物品"), b -> shuffleInventory())
			.bounds(left + 650, 70, Math.max(90, panelWidth - 650), 20).build());

		EditBox durability = numberBox(left, 130, durabilityEvery, 4, value -> durabilityEvery = value);
		EditBox hungerInterval = numberBox(left + 200, 130, hungerEvery, 4, value -> hungerEvery = value);
		EditBox hungerAmount = numberBox(left + 400, 130, hungerCost, 2, value -> hungerCost = value);
		EditBox strength = numberBox(left + 600, 130, rushStrength, 5, value -> rushStrength = value);
		strength.setWidth(Math.max(80, panelWidth - 600));

		addRenderableWidget(Button.builder(Component.literal("保存游戏增强设置"), b -> saveGameplay())
			.bounds(left, 190, 220, 20).build());
	}

	private void buildShortcutPanel(int left, int panelWidth) {
		EditBox primary = addRenderableWidget(new EditBox(font, left, 85, 120, 20,
			Component.literal("快捷键一")));
		primary.setMaxLength(1);
		primary.setValue(primaryKey);
		primary.setResponder(value -> primaryKey = value);
		EditBox secondary = addRenderableWidget(new EditBox(font, left + 135, 85, 120, 20,
			Component.literal("快捷键二")));
		secondary.setMaxLength(1);
		secondary.setValue(secondaryKey);
		secondary.setResponder(value -> secondaryKey = value);
		EditBox zoom = addRenderableWidget(new EditBox(font, left + 270, 85, 120, 20,
			Component.literal("缩放快捷键")));
		zoom.setMaxLength(1);
		zoom.setValue(zoomKey);
		zoom.setResponder(value -> zoomKey = value);
		EditBox navigator = addRenderableWidget(new EditBox(font, left + 405, 85, 120, 20,
			Component.literal("导航快捷键")));
		navigator.setMaxLength(1);
		navigator.setValue(navigatorKey);
		navigator.setResponder(value -> navigatorKey = value);
		EditBox positions = addRenderableWidget(new EditBox(font, left + 540, 85, 90, 20, Component.literal("AI 菜单键")));
		positions.setMaxLength(3); positions.setValue(positionsKey); positions.setResponder(value -> positionsKey = value);
		EditBox minigames = addRenderableWidget(new EditBox(font, left + 645, 85,
			Math.max(80, panelWidth - 645), 20, Component.literal("小游戏中心")));
		minigames.setMaxLength(1); minigames.setValue(minigameKey); minigames.setResponder(value -> minigameKey = value);

		addRenderableWidget(Button.builder(Component.literal("保存快捷键"), b -> saveShortcuts())
			.bounds(left, 130, 180, 20).build());
		if (backend == UiBackend.CLOTH_CONFIG) addRenderableWidget(Button.builder(Component.literal("Cloth 快捷栏：" + (clothNavigationTop ? "顶部" : "左侧")), b -> { clothNavigationTop = !clothNavigationTop; rebuildPanel(); }).bounds(left + 190, 130, 210, 20).build());
	}

	private void buildAdvancedPanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("结构/群系指南针："
			+ (worldNavigatorEnabled ? "开启" : "关闭")), button -> {
			worldNavigatorEnabled = !worldNavigatorEnabled;
			rebuildPanel();
		}).bounds(left, 70, 250, 20).build());
		addRenderableWidget(Button.builder(Component.literal("仁慈的虚空："
			+ (mercifulVoidEnabled ? "开启" : "关闭")), button -> {
			mercifulVoidEnabled = !mercifulVoidEnabled;
			rebuildPanel();
		}).bounds(left + 260, 70, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("原版最大世界边界："
			+ (maximumWorldBorderEnabled ? "开启" : "关闭")), button -> {
			maximumWorldBorderEnabled = !maximumWorldBorderEnabled;
			rebuildPanel();
		}).bounds(left, 115, 250, 20).build());
		addRenderableWidget(Button.builder(Component.literal("打开导航界面"), button -> {
			if (minecraft != null) com.example.ai_companion.client.navigation.NavigationClientController
				.open(minecraft, this);
		}).bounds(left + 260, 115, 220, 20).build());
		addRenderableWidget(Button.builder(Component.literal("保存高危区域设置"), button ->
			saveWorldFeatures()).bounds(left, 170, 250, 20).build());
		status = "高危区域：真正无限高度与64位坐标超出Minecraft区块格式；本版不会伪造该能力";
	}

	private void buildClientPanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("望远镜生物发光："
				+ (spyglassHighlightEnabled ? "开启" : "关闭")), b -> {
			spyglassHighlightEnabled = !spyglassHighlightEnabled;
			rebuildPanel();
		}).bounds(left, 65, 260, 20).build());
		addRenderableWidget(Button.builder(Component.literal("屏幕缩放："
				+ (screenZoomEnabled ? "开启" : "关闭")), b -> {
			screenZoomEnabled = !screenZoomEnabled;
			rebuildPanel();
		}).bounds(left + 270, 65, 220, 20).build());
		numberBox(left, 115, spyglassRadiusChunks, 2, value -> spyglassRadiusChunks = value);
		numberBox(left + 150, 115, spyglassHoldSeconds, 2, value -> spyglassHoldSeconds = value);
		numberBox(left + 300, 115, spyglassDurationSeconds, 3, value -> spyglassDurationSeconds = value);
		addRenderableWidget(Button.builder(Component.literal("目标：" + spyglassTargetCondition.displayName()), b -> {
			var values = com.example.ai_companion.spyglass.SpyglassTargetCondition.values();
			spyglassTargetCondition = values[(spyglassTargetCondition.ordinal() + 1) % values.length];
			rebuildPanel();
		}).bounds(left + 450, 115, 180, 20).build());
		numberBox(left, 145, spyglassCooldownSeconds, 3, value -> spyglassCooldownSeconds = value);
		numberBox(left + 150, 145, spyglassMaxTargets, 4, value -> spyglassMaxTargets = value);
		numberBox(left, 170, zoomFactor, 5, value -> zoomFactor = value);
		numberBox(left + 200, 170, zoomTransitionSeconds, 5,
			value -> zoomTransitionSeconds = value);
		addRenderableWidget(Button.builder(Component.literal("保存客户端增强设置"), b -> saveClientEnhancements())
			.bounds(left, 215, 220, 20).build());
	}

	private void buildPerformancePanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("客户端附加渲染优化："
				+ (performanceOptimizerEnabled ? "开启" : "关闭")), b -> {
			performanceOptimizerEnabled = !performanceOptimizerEnabled;
			rebuildPanel();
		}).bounds(left, 70, 260, 20).build());
		addRenderableWidget(Button.builder(Component.literal("距离模式："
				+ (adaptiveExtraRenderDistance ? "自适应" : "固定")), b -> {
			adaptiveExtraRenderDistance = !adaptiveExtraRenderDistance;
			rebuildPanel();
		}).bounds(left + 270, 70, 220, 20).build());
		numberBox(left, 130, performanceTargetFps, 3, value -> performanceTargetFps = value);
		numberBox(left + 200, 130, extraRenderDistance, 3, value -> extraRenderDistance = value);
		numberBox(left + 400, 130, minimumExtraRenderDistance, 3,
			value -> minimumExtraRenderDistance = value);
		addRenderableWidget(Button.builder(Component.literal("保存性能优化设置"), b -> savePerformance())
			.bounds(left, 190, 220, 20).build());
		status = ClientPerformanceController.statusText();
	}

	private EditBox numberBox(int x, int y, String value, int maxLength,
							  java.util.function.Consumer<String> responder) {
		EditBox box = addRenderableWidget(new EditBox(font, x, y, 120, 20, Component.literal("数值")));
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setResponder(responder);
		return box;
	}

	private void createMany() {
		try {
			int count = Integer.parseInt(createCount);
			if (count < 1 || count > 20) throw new IllegalArgumentException("数量应为 1-20");
			if (!baseName.matches("[A-Za-z0-9_]{1,13}")) {
				throw new IllegalArgumentException("名称前缀应为 1-13 位字母、数字或下划线");
			}
			UiActionClient.send("agent.create_many", baseName, Integer.toString(count));
			status = "创建请求已发送；请以服务器返回的已验证数量与坐标为准";
		} catch (RuntimeException error) {
			status = "生成失败: " + error.getMessage();
		}
	}

	private void cycleMode() {
		selectedMode = switch (selectedMode) {
			case HUNTER -> AgentMode.TEAMMATE;
			case TEAMMATE -> AgentMode.PVP_COACH;
			case PVP_COACH, IDLE -> AgentMode.HUNTER;
		};
		rebuildPanel();
	}

	private String modeLabel() {
		return switch (selectedMode) {
			case HUNTER -> "追杀";
			case TEAMMATE -> "队友";
			case PVP_COACH -> "PvP 教练";
			case IDLE -> "空闲";
		};
	}

	private void togglePlayers() {
		playersExpanded = !playersExpanded;
		rebuildPanel();
	}

	private void choosePlayer(String player) {
		selectedPlayer = player;
		playersExpanded = false;
		rebuildPanel();
	}

	private List<String> onlinePlayers() {
		if (minecraft == null || minecraft.getConnection() == null) return List.of();
		return minecraft.getConnection().getOnlinePlayers().stream()
			.map(info -> info.getProfile().name()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

	private void cyclePrompt() {
		if (promptIds.isEmpty()) return;
		promptIndex = Math.floorMod(promptIndex + 1, promptIds.size());
		loadPrompt(promptIds.get(promptIndex));
		rebuildPanel();
	}

	private void applyAgentSetup() {
		try {
			validateAgent();
			settings.setDefaultAgentMode(selectedMode);
			settings.save();
			if (selectedPlayer.isBlank()) throw new IllegalArgumentException("请展开并选择当前世界玩家");
			UiActionClient.send("agent.mode", agentName, selectedMode.name(), selectedPlayer);
			if (!promptId.isBlank()) UiActionClient.send("prompt.assign", agentName, promptId);
			status = "已应用 " + modeLabel() + "，目标 " + selectedPlayer + "，提示词 " + promptId;
		} catch (RuntimeException | IOException error) {
			status = "应用失败: " + error.getMessage();
		}
	}

	private void setIdle() {
		try {
			validateAgent();
			UiActionClient.send("agent.idle", agentName);
			status = "已请求将 " + agentName + " 设为空闲";
		} catch (RuntimeException error) {
			status = "设置失败: " + error.getMessage();
		}
	}

	private void askAgent() {
		try {
			validateAgent();
			if (instruction.isBlank()) throw new IllegalArgumentException("任务不能为空");
			UiActionClient.send("agent.ask", agentName, instruction);
			status = "AI 正在思考";
		} catch (RuntimeException error) {
			status = "请求失败: " + error.getMessage();
		}
	}

	private void validateAgent() {
		if (!agentName.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("请输入有效的 AI 名称");
		}
	}

	private void selectPrompt(int direction) {
		if (promptIds.isEmpty()) return;
		promptIndex = Math.floorMod(promptIndex + direction, promptIds.size());
		loadPrompt(promptIds.get(promptIndex));
		rebuildPanel();
	}

	private void newPrompt() {
		int suffix = 1;
		String id;
		do id = "custom_" + suffix++;
		while (promptStore.contains(id));
		promptId = id;
		promptText = "";
		rebuildPanel();
		status = "新提示词草稿；编辑后点击保存";
	}

	private void savePrompt() {
		capturePromptDraft();
		try {
			String id = PromptStore.validateId(promptId);
			promptStore.put(id, promptText);
			UiActionClient.send("prompt.put", id, promptText);
			reloadPromptIds();
			promptIndex = Math.max(0, promptIds.indexOf(id));
			status = "已保存并请求写入服务器: " + id;
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void deletePrompt() {
		try {
			String id = PromptStore.validateId(promptId);
			promptStore.remove(id);
			UiActionClient.send("prompt.remove", id);
			reloadPromptIds();
			if (!promptIds.isEmpty()) loadPrompt(promptIds.getFirst());
			rebuildPanel();
			status = "已删除: " + id;
		} catch (RuntimeException | IOException error) {
			status = "删除失败: " + error.getMessage();
		}
	}

	private void resetPrompt() {
		try {
			String id = PromptStore.validateId(promptId);
			promptStore.reset(id);
			UiActionClient.send("prompt.reset", id);
			loadPrompt(id);
			rebuildPanel();
			status = "已恢复内置预设: " + id;
		} catch (RuntimeException | IOException error) {
			status = "恢复失败: " + error.getMessage();
		}
	}

	private void assignPrompt() {
		capturePromptDraft();
		try {
			validateAgent();
			if (!promptStore.contains(promptId)) throw new IllegalArgumentException("请先保存提示词");
			UiActionClient.send("prompt.assign", agentName, promptId);
			status = "已请求将 " + promptId + " 分配给 " + agentName;
		} catch (RuntimeException error) {
			status = "分配失败: " + error.getMessage();
		}
	}

	private void capturePromptDraft() {
		if (idBox != null) promptId = idBox.getValue();
		if (promptBox != null) promptText = promptBox.getValue();
		if (agentBox != null) agentName = agentBox.getValue();
	}

	private void reloadPromptIds() {
		promptIds = new ArrayList<>(promptStore.ids());
	}

	private void loadPrompt(String id) {
		promptId = id;
		promptText = promptStore.get(id);
		promptIndex = Math.max(0, promptIds.indexOf(id));
	}

	private void saveShortcuts() {
		try {
			settings.primaryKey = ClientSettings.normalizeKey(primaryKey, "V");
			settings.secondaryKey = ClientSettings.normalizeKey(secondaryKey, "B");
			settings.positionsKey = ClientSettings.normalizeFunctionKey(positionsKey, "F8");
			settings.minigameKey = ClientSettings.normalizeKey(minigameKey, "M");
			settings.clothNavigationTop = clothNavigationTop;
			settings.zoomKey = ClientSettings.normalizeKey(zoomKey, "C");
			settings.navigatorKey = ClientSettings.normalizeKey(navigatorKey, "G");
			settings.save();
			primaryKey = settings.primaryKey;
			secondaryKey = settings.secondaryKey;
			positionsKey = settings.positionsKey;
			minigameKey = settings.minigameKey;
			zoomKey = settings.zoomKey;
			navigatorKey = settings.navigatorKey;
			status = "已保存并立即生效；界面 " + primaryKey + "+" + secondaryKey + "，AI 控制台 "
				+ positionsKey + "，小游戏 " + minigameKey + "，缩放 " + zoomKey + "，导航 " + navigatorKey;
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void saveWorldFeatures() {
		try {
			settings.worldNavigatorEnabled = worldNavigatorEnabled;
			settings.navigatorKey = ClientSettings.normalizeKey(navigatorKey, "G");
			settings.mercifulVoidEnabled = mercifulVoidEnabled;
			settings.maximumWorldBorderEnabled = maximumWorldBorderEnabled;
			settings.save();
			UiActionClient.send("world.save", Boolean.toString(worldNavigatorEnabled),
				Boolean.toString(mercifulVoidEnabled), Boolean.toString(maximumWorldBorderEnabled));
			status = "设置已保存；服务器端高危选项需要管理员权限";
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void saveGameplay() {
		try {
			settings.goldenSpearRushEnabled = rushEnabled;
			settings.durabilityEvery = parseInt(durabilityEvery, 1, 1000, "耐久间隔");
			settings.hungerEvery = parseInt(hungerEvery, 1, 1000, "饥饿间隔");
			settings.hungerCost = parseInt(hungerCost, 0, 20, "饥饿消耗");
			settings.rushStrength = parseDouble(rushStrength, 0.1, 4.0, "突进强度");
			settings.flexibleEquipmentEnabled = flexibleEquipmentEnabled;
			settings.sprintJumpEnabled = sprintJumpEnabled;
			settings.save();
			UiActionClient.send("gameplay.save", Boolean.toString(rushEnabled),
				Integer.toString(settings.durabilityEvery), Integer.toString(settings.hungerEvery),
				Integer.toString(settings.hungerCost), Double.toString(settings.rushStrength),
				Boolean.toString(flexibleEquipmentEnabled));
			status = "已保存金矛突进、任意物品装备与持续疾跑跳跃设置";
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void shuffleInventory() {
		try {
			if (!flexibleEquipmentEnabled) throw new IllegalStateException("请先开启任意物品装备模式");
			settings.flexibleEquipmentEnabled = true;
			settings.save();
			UiActionClient.send("gameplay.save", Boolean.toString(rushEnabled),
				Integer.toString(settings.durabilityEvery), Integer.toString(settings.hungerEvery),
				Integer.toString(settings.hungerCost), Double.toString(settings.rushStrength), "true");
			UiActionClient.send("inventory.shuffle");
			status = "已请求打乱背包栏、装备栏和副手；快捷栏不会变化";
		} catch (RuntimeException | IOException error) {
			status = "打乱失败: " + error.getMessage();
		}
	}

	private void saveClientEnhancements() {
		try {
			settings.spyglassHighlightEnabled = spyglassHighlightEnabled;
			settings.spyglassRadiusChunks = parseInt(spyglassRadiusChunks, 1, 32, "望远镜半径");
			settings.spyglassHoldSeconds = parseInt(spyglassHoldSeconds, 1, 10, "望远镜观察时间");
			settings.spyglassDurationSeconds = parseInt(spyglassDurationSeconds, 1, 600, "发光持续时间");
			settings.spyglassTargetCondition = spyglassTargetCondition.name();
			settings.spyglassCooldownSeconds = parseInt(spyglassCooldownSeconds, 1, 600, "望远镜冷却时间");
			settings.spyglassMaxTargets = parseInt(spyglassMaxTargets, 1, 1024, "望远镜单次命中上限");
			settings.screenZoomEnabled = screenZoomEnabled;
			settings.zoomKey = ClientSettings.normalizeKey(zoomKey, "C");
			settings.zoomFactor = parseDouble(zoomFactor, 1.5, 12.0, "缩放倍率");
			settings.zoomTransitionSeconds = parseDouble(zoomTransitionSeconds, 0.0, 1.0, "过渡时间");
			settings.save();
			UiActionClient.send("spyglass.save", Boolean.toString(settings.spyglassHighlightEnabled),
				Integer.toString(settings.spyglassRadiusChunks), Integer.toString(settings.spyglassHoldSeconds),
				Integer.toString(settings.spyglassDurationSeconds), settings.spyglassTargetCondition,
				Integer.toString(settings.spyglassCooldownSeconds), Integer.toString(settings.spyglassMaxTargets));
			status = "已保存望远镜发光、缩放与疾跑跳跃保持设置";
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void savePerformance() {
		try {
			settings.clientPerformanceOptimizerEnabled = performanceOptimizerEnabled;
			settings.adaptiveExtraRenderDistance = adaptiveExtraRenderDistance;
			settings.performanceTargetFps = parseInt(performanceTargetFps, 30, 240, "目标帧率");
			settings.extraRenderDistance = parseInt(extraRenderDistance, 16, 256, "最大距离");
			settings.minimumExtraRenderDistance = parseInt(minimumExtraRenderDistance, 16,
				settings.extraRenderDistance, "最小距离");
			settings.save();
			status = "已保存；" + ClientPerformanceController.statusText();
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void saveApi() {
		try {
			if (apiBase.isBlank() || (!apiBase.startsWith("https://") && !apiBase.startsWith("http://"))) {
				throw new IllegalArgumentException("API 地址必须以 http:// 或 https:// 开头");
			}
			if (apiModel.isBlank() || apiModel.contains(" ")) {
				throw new IllegalArgumentException("模型名称不能为空或包含空格");
			}
			settings.apiBase = apiBase.strip();
			settings.model = apiModel.strip();
			settings.save();
			UiActionClient.send("config.save", settings.apiBase, settings.model, apiToken.strip());
			apiToken = "";
			status = "已请求保存 API 配置；令牌不会写入客户端设置";
		} catch (RuntimeException | IOException error) {
			status = "API 保存失败: " + error.getMessage();
		}
	}

	private void requestApiConfig() {
		if (apiConfigRequested) return;
		apiConfigRequested = true;
		try {
			UiActionClient.send("config.read");
			status = "正在从服务器读取已保存的 API 配置…";
		} catch (RuntimeException error) {
			status = "读取服务器 API 配置失败: " + error.getMessage();
		}
	}

	private static int parseInt(String value, int min, int max, String label) {
		int parsed = Integer.parseInt(value);
		if (parsed < min || parsed > max) throw new IllegalArgumentException(label + "超出范围");
		return parsed;
	}

	private static double parseDouble(String value, double min, double max, String label) {
		double parsed = Double.parseDouble(value);
		if (parsed < min || parsed > max) throw new IllegalArgumentException(label + "超出范围");
		return parsed;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		boolean topNavigation = backend == UiBackend.CLOTH_CONFIG && clothNavigationTop;
		if (!topNavigation) graphics.centeredText(font, title, width / 2, 9, 0xFFFFFF);
		int fullWidth = Math.min(920, width - 24);
		int left = (width - fullWidth) / 2 + (topNavigation ? 0 : 158);
		int panelWidth = fullWidth - (topNavigation ? 0 : 158);
		switch (tab) {
			case AI_SYSTEM -> {
				switch (aiSection) {
					case MANAGEMENT -> {
						graphics.text(font, "批量生成 AI（名称自动加 1、2、3…）", left, 56, 0xA0A0A0);
						graphics.text(font, "AI 模式、当前玩家目标与提示词", left, 106, 0xA0A0A0);
						graphics.text(font, "立即请求一次 AI 决策", left, 191, 0xA0A0A0);
					}
					case MAID -> graphics.text(font,
						"AI 女仆会使用独立主人身份、女仆提示词和头顶心情对话标记",
						left, 58, 0xFFA0A0A0);
					case API -> {
						graphics.text(font, "OpenAI Chat Completions 兼容 API 地址", left, 61, 0xA0A0A0);
						graphics.text(font, "模型名称", left, 111, 0xA0A0A0);
						graphics.text(font, "令牌（可留空；输入后仅发送到服务器，不保存在客户端）",
							left, 161, 0xA0A0A0);
					}
					case PROMPTS -> {
						graphics.text(font, "提示词 ID", left, 55, 0xA0A0A0);
						graphics.text(font, "分配给 AI（可选）", left + panelWidth / 2 + 5, 55, 0xA0A0A0);
					}
				}
			}
			case GAMEPLAY -> {
				graphics.text(font, "金矛无需附魔即可获得原版二级突进强度；其他材质仍需突进附魔",
					left, 55, 0xA0A0A0);
				graphics.text(font, "每多少次消耗1耐久", left, 106, 0xA0A0A0);
				graphics.text(font, "每多少次消耗饥饿", left + 200, 106, 0xA0A0A0);
				graphics.text(font, "饥饿点数（1鸡腿=2）", left + 400, 106, 0xA0A0A0);
				graphics.text(font, "突进强度", left + 600, 106, 0xA0A0A0);
			}
			case SHORTCUTS -> graphics.text(font, "打开 UI 双键与屏幕缩放键（仅支持 A-Z）", left, 62, 0xA0A0A0);
			case CLIENT -> {
				graphics.text(font,
					"望远镜连续观察后由服务器施加原版发光效果；缩放默认关闭",
					left, 55, 0xA0A0A0);
				graphics.text(font, "半径区块（1-32）", left, 101, 0xA0A0A0);
				graphics.text(font, "观察秒数（1-10）", left + 150, 101, 0xA0A0A0);
				graphics.text(font, "持续秒数（1-600）", left + 300, 101, 0xA0A0A0);
				graphics.text(font, "缩放倍率（1.5-12）", left, 156, 0xA0A0A0);
				graphics.text(font, "过渡秒数（0-1）", left + 200, 156, 0xA0A0A0);
			}
			case COMPATIBILITY -> graphics.text(font, "UI 后端：" + backend.displayName(), left, 55, 0xA0A0A0);
			case MINIGAMES -> {
				graphics.text(font, "贪吃蛇：最高 " + minigameProgress.snakeHighScore + " · "
					+ minigameProgress.snakeTitle() + "    俄罗斯方块：最高 "
					+ minigameProgress.tetrisHighScore + " · 最佳消行 "
					+ minigameProgress.tetrisBestLines, left, 143, 0xFFCFD8DC);
				graphics.text(font, "扫雷：最佳 " + minigameProgress.minesweeperBestTime() + " · 胜场 "
					+ minigameProgress.minesweeperWins + "    2048：最高 "
					+ minigameProgress.game2048HighScore + " · 最大 "
					+ minigameProgress.game2048BestTile, left, 159, 0xFFCFD8DC);
				graphics.text(font, "AI 猜拳：胜 " + minigameProgress.rpsWins + " / 负 "
					+ minigameProgress.rpsLosses + " / 平 " + minigameProgress.rpsDraws
					+ " · 最佳连胜 " + minigameProgress.rpsBestStreak,
					left, 175, 0xFFFFD54F);
			}
			case PERFORMANCE -> {
				graphics.text(font, "只限制本模组的装备位附加 3D 模型；默认关闭",
					left, 55, 0xA0A0A0);
				graphics.text(font, "目标 FPS（30-240）", left, 106, 0xA0A0A0);
				graphics.text(font, "最大距离（16-256）", left + 200, 106, 0xA0A0A0);
				graphics.text(font, "最小距离（16-最大值）", left + 400, 106, 0xA0A0A0);
			}
			case LEISURE, ADVANCED -> { }
		}
		graphics.text(font, status, 12, height - 22, status.contains("失败") ? 0xFF7777 : 0xA8E6A3);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
