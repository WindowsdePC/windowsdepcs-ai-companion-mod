package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.client.minigame.Game2048Screen;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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

	private enum AiSection { MANAGEMENT, API, PROMPTS }

	private final PromptStore promptStore;
	private final ClientSettings settings;
	private final UiBackend backend;
	private final MinigameProgress minigameProgress;
	private Tab tab = Tab.AI_SYSTEM;
	private AiSection aiSection = AiSection.MANAGEMENT;
	private String status = "修改服务器设置和分配 AI 需要管理员权限";

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
	private boolean glowingHitboxesEnabled;
	private String durabilityEvery;
	private String hungerEvery;
	private String hungerCost;
	private String rushStrength;
	private String primaryKey;
	private String secondaryKey;
	private String apiBase;
	private String apiModel;
	private String apiToken = "";

	private EditBox idBox;
	private EditBox agentBox;
	private MultiLineEditBox promptBox;

	public PromptConfigScreen(PromptStore promptStore, ClientSettings settings, UiBackend backend) {
		super(Component.literal("WindowsdePC's AI Companion Mod · 统一设置"));
		this.promptStore = promptStore;
		this.settings = settings;
		this.backend = backend;
		this.minigameProgress = MinigameProgress.load();
		rushEnabled = settings.goldenSpearRushEnabled;
		glowingHitboxesEnabled = settings.f3BGlowingHitboxesEnabled;
		durabilityEvery = Integer.toString(settings.durabilityEvery);
		hungerEvery = Integer.toString(settings.hungerEvery);
		hungerCost = Integer.toString(settings.hungerCost);
		rushStrength = Double.toString(settings.rushStrength);
		primaryKey = settings.primaryKey;
		secondaryKey = settings.secondaryKey;
		apiBase = settings.apiBase;
		apiModel = settings.model;
		selectedMode = settings.defaultAgentMode();
		reloadPromptIds();
		if (!promptIds.isEmpty()) loadPrompt(promptIds.getFirst());
	}

	@Override
	protected void init() {
		rebuildPanel();
	}

	private void rebuildPanel() {
		clearWidgets();
		int fullWidth = Math.min(920, width - 24);
		int left = (width - fullWidth) / 2;
		int sidebarWidth = 148;
		int panelLeft = left + sidebarWidth + 10;
		int panelWidth = fullWidth - sidebarWidth - 10;
		int y = 28;
		for (Tab candidate : Tab.values()) {
			String marker = candidate == tab ? "▶ " : "  ";
			addRenderableWidget(Button.builder(Component.literal(marker + candidate.label),
				button -> switchTab(candidate)).bounds(left, y, sidebarWidth, 20).build());
			y += 23;
		}

		switch (tab) {
			case AI_SYSTEM -> buildAiSystemPanel(panelLeft, panelWidth);
			case SHORTCUTS -> buildShortcutPanel(panelLeft, panelWidth);
			case GAMEPLAY -> buildGameplayPanel(panelLeft, panelWidth);
			case CLIENT -> buildClientPanel(panelLeft, panelWidth);
			case MINIGAMES -> buildMinigamePanel(panelLeft, panelWidth);
			case LEISURE -> buildPlaceholderPanel("休闲系统将在后续独立功能版本中逐项开放");
			case PERFORMANCE -> buildPlaceholderPanel("性能优化选项将在对应功能实现后加入");
			case COMPATIBILITY -> buildPlaceholderPanel("当前 UI 后端：" + backend.displayName()
				+ "；Simple Voice Chat 为可选兼容项");
			case ADVANCED -> buildPlaceholderPanel("高级设置保留给调试、迁移与实验功能");
		}
		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
			.bounds(left + fullWidth - 90, height - 25, 90, 20).build());
	}

	private void buildAiSystemPanel(int left, int panelWidth) {
		int sectionWidth = (panelWidth - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("AI 管理"), b -> switchAiSection(AiSection.MANAGEMENT))
			.bounds(left, 28, sectionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("API"), b -> switchAiSection(AiSection.API))
			.bounds(left + sectionWidth + 4, 28, sectionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("提示词"), b -> switchAiSection(AiSection.PROMPTS))
			.bounds(left + (sectionWidth + 4) * 2, 28, sectionWidth, 20).build());
		switch (aiSection) {
			case MANAGEMENT -> buildAiPanel(left, panelWidth);
			case API -> buildApiPanel(left, panelWidth);
			case PROMPTS -> buildPromptPanel(left, panelWidth);
		}
	}

	private void switchAiSection(AiSection next) {
		capturePromptDraft();
		aiSection = next;
		playersExpanded = false;
		rebuildPanel();
	}

	private void buildPlaceholderPanel(String message) {
		status = message;
	}

	private void buildMinigamePanel(int left, int panelWidth) {
		int cardWidth = (panelWidth - 14) / 2;
		addRenderableWidget(Button.builder(Component.literal("开始：贪吃蛇"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new SnakeScreen(this, minigameProgress));
		}).bounds(left, 56, cardWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("开始：Minecraft 俄罗斯方块"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new TetrisScreen(this, minigameProgress));
		}).bounds(left + cardWidth + 14, 56, cardWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("开始：Minecraft 方块扫雷"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new MinesweeperScreen(this, minigameProgress));
		}).bounds(left, 84, cardWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("开始：2048"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new Game2048Screen(this, minigameProgress));
		}).bounds(left + cardWidth + 14, 84, cardWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("开始：AI 猜拳"), button -> {
			if (minecraft != null) minecraft.setScreenAndShow(new RockPaperScissorsScreen(this,
				minigameProgress));
		}).bounds(left + panelWidth / 4, 112, panelWidth / 2, 20).build());
		status = "0.5.4 已完成设计文档小游戏中心 5/5；记录保存在客户端配置目录";
	}

	private void buildApiPanel(int left, int panelWidth) {
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
			sendCommand("aiplayer config status")).bounds(left + 190, 220, 180, 20).build());
	}

	private void switchTab(Tab next) {
		capturePromptDraft();
		tab = next;
		playersExpanded = false;
		rebuildPanel();
	}

	private void buildAiPanel(int left, int panelWidth) {
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

		EditBox durability = numberBox(left, 120, durabilityEvery, 4, value -> durabilityEvery = value);
		EditBox hungerInterval = numberBox(left + 200, 120, hungerEvery, 4, value -> hungerEvery = value);
		EditBox hungerAmount = numberBox(left + 400, 120, hungerCost, 2, value -> hungerCost = value);
		EditBox strength = numberBox(left + 600, 120, rushStrength, 5, value -> rushStrength = value);
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

		addRenderableWidget(Button.builder(Component.literal("保存快捷键"), b -> saveShortcuts())
			.bounds(left, 130, 180, 20).build());
	}

	private void buildClientPanel(int left, int panelWidth) {
		addRenderableWidget(Button.builder(Component.literal("F3+B 发光轮廓："
				+ (glowingHitboxesEnabled ? "开启" : "关闭")), b -> {
			glowingHitboxesEnabled = !glowingHitboxesEnabled;
			rebuildPanel();
		}).bounds(left, 75, 260, 20).build());
		addRenderableWidget(Button.builder(Component.literal("保存客户端增强设置"), b -> saveClientEnhancements())
			.bounds(left, 120, 220, 20).build());
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
			sendCommand("aiplayer create-many " + baseName + " " + count);
			status = "已请求生成 " + count + " 个 AI";
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
			String modeCommand = switch (selectedMode) {
				case HUNTER -> "hunt";
				case TEAMMATE -> "team";
				case PVP_COACH -> "coach";
				case IDLE -> "idle";
			};
			sendCommand("aiplayer " + modeCommand + " " + agentName + " " + selectedPlayer);
			if (!promptId.isBlank()) sendCommand("aiplayer prompt assign " + agentName + " " + promptId);
			status = "已应用 " + modeLabel() + "，目标 " + selectedPlayer + "，提示词 " + promptId;
		} catch (RuntimeException | IOException error) {
			status = "应用失败: " + error.getMessage();
		}
	}

	private void setIdle() {
		try {
			validateAgent();
			sendCommand("aiplayer idle " + agentName);
			status = "已请求将 " + agentName + " 设为空闲";
		} catch (RuntimeException error) {
			status = "设置失败: " + error.getMessage();
		}
	}

	private void askAgent() {
		try {
			validateAgent();
			if (instruction.isBlank()) throw new IllegalArgumentException("任务不能为空");
			sendCommand("aiplayer ask " + agentName + " " + instruction);
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
			sendCommand("aiplayer prompt put " + id + " " + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(promptText.getBytes(StandardCharsets.UTF_8)));
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
			sendCommand("aiplayer prompt remove " + id);
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
			sendCommand("aiplayer prompt reset " + id);
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
			sendCommand("aiplayer prompt assign " + agentName + " " + promptId);
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
			settings.save();
			primaryKey = settings.primaryKey;
			secondaryKey = settings.secondaryKey;
			status = "已保存；新快捷键为 " + primaryKey + "+" + secondaryKey;
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
			settings.save();
			sendCommand("aiplayer feature enabled " + rushEnabled);
			sendCommand("aiplayer feature durability-every " + settings.durabilityEvery);
			sendCommand("aiplayer feature hunger-every " + settings.hungerEvery);
			sendCommand("aiplayer feature hunger-cost " + settings.hungerCost);
			sendCommand("aiplayer feature strength " + settings.rushStrength);
			status = "已保存金矛突进设置";
		} catch (RuntimeException | IOException error) {
			status = "保存失败: " + error.getMessage();
		}
	}

	private void saveClientEnhancements() {
		try {
			settings.f3BGlowingHitboxesEnabled = glowingHitboxesEnabled;
			settings.save();
			status = "已保存；F3+B 将" + (glowingHitboxesEnabled ? "使用原版发光轮廓" : "恢复原版碰撞箱");
		} catch (IOException error) {
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
			sendCommand("aiplayer config endpoint " + settings.apiBase);
			sendCommand("aiplayer config model " + settings.model);
			if (!apiToken.isBlank()) {
				sendCommand("aiplayer config token " + apiToken.strip());
				apiToken = "";
			}
			status = "已请求保存 API 配置；令牌不会写入客户端设置";
		} catch (RuntimeException | IOException error) {
			status = "API 保存失败: " + error.getMessage();
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

	private void sendCommand(String command) {
		if (minecraft == null || minecraft.getConnection() == null) {
			throw new IllegalStateException("当前没有服务器连接");
		}
		minecraft.getConnection().sendCommand(command);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 9, 0xFFFFFF);
		int fullWidth = Math.min(920, width - 24);
		int left = (width - fullWidth) / 2 + 158;
		int panelWidth = fullWidth - 158;
		switch (tab) {
			case AI_SYSTEM -> {
				switch (aiSection) {
					case MANAGEMENT -> {
						graphics.text(font, "批量生成 AI（名称自动加 1、2、3…）", left, 56, 0xA0A0A0);
						graphics.text(font, "AI 模式、当前玩家目标与提示词", left, 106, 0xA0A0A0);
						graphics.text(font, "立即请求一次 AI 决策", left, 191, 0xA0A0A0);
					}
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
			case SHORTCUTS -> graphics.text(font, "打开 UI 快捷键（仅支持 A-Z 双键组合）", left, 62, 0xA0A0A0);
			case CLIENT -> graphics.text(font,
				"开启后，按 F3+B 时隐藏实体线框并调用原版发光轮廓；默认开启且仅本地生效",
				left, 55, 0xA0A0A0);
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
			case LEISURE, PERFORMANCE, ADVANCED -> { }
		}
		graphics.text(font, status, 12, height - 22, status.contains("失败") ? 0xFF7777 : 0xA8E6A3);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
