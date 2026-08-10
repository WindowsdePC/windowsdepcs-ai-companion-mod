package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.agent.AgentPosition;
import com.example.ai_companion.config.PromptStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Dedicated prompt-assignment popup restored for selecting an AI, mode, target and preset together. */
public final class PromptAssignmentScreen extends Screen {
	private final Screen parent;
	private final PromptStore prompts;
	private final ClientSettings settings;
	private List<AgentPosition> agents = List.of();
	private List<String> promptIds = List.of();
	private List<String> players = List.of();
	private int agentIndex;
	private int promptIndex;
	private int playerIndex;
	private AgentMode mode;
	private String status = "选择 AI、模式、目标和提示词后点击应用";
	private long positionsRevision = -1;
	private long uiResultRevision = UiActionClient.revision();

	public PromptAssignmentScreen(Screen parent, PromptStore prompts, ClientSettings settings) {
		super(Component.literal("AI 提示词分配"));
		this.parent = parent;
		this.prompts = prompts;
		this.settings = settings;
		this.mode = settings.defaultAgentMode();
	}

	@Override protected void init() {
		refreshData();
		buildWidgets();
		if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
	}

	@Override public void tick() {
		super.tick();
		if (positionsRevision != AgentPositionHud.revision()) {
			positionsRevision = AgentPositionHud.revision();
			refreshData();
			rebuildWidgets();
		}
		if (uiResultRevision != UiActionClient.revision()) {
			uiResultRevision = UiActionClient.revision();
			status = UiActionClient.lastMessage();
		}
	}

	private void refreshData() {
		agents = AgentPositionHud.snapshot();
		promptIds = new ArrayList<>(prompts.ids());
		players = minecraft == null || minecraft.getConnection() == null ? List.of()
			: minecraft.getConnection().getOnlinePlayers().stream()
				.map(info -> info.getProfile().name()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
		agentIndex = agents.isEmpty() ? 0 : Math.floorMod(agentIndex, agents.size());
		promptIndex = promptIds.isEmpty() ? 0 : Math.floorMod(promptIndex, promptIds.size());
		playerIndex = players.isEmpty() ? 0 : Math.floorMod(playerIndex, players.size());
	}

	private void buildWidgets() {
		int panelWidth = Math.min(620, width - 30);
		int left = (width - panelWidth) / 2;
		addRenderableWidget(Button.builder(Component.literal("AI：" + selectedAgent()), b -> {
			if (!agents.isEmpty()) agentIndex = (agentIndex + 1) % agents.size();
			rebuildWidgets();
		}).bounds(left, 65, panelWidth, 22).build());
		addRenderableWidget(Button.builder(Component.literal("模式：" + modeLabel()), b -> {
			mode = switch (mode) {
				case SURVIVAL -> AgentMode.HUNTER;
				case HUNTER -> AgentMode.TEAMMATE;
				case TEAMMATE -> AgentMode.PVP_COACH;
				case PVP_COACH -> AgentMode.IDLE;
				case IDLE -> AgentMode.SURVIVAL;
			};
			rebuildWidgets();
		}).bounds(left, 97, (panelWidth - 10) / 2, 22).build());
		addRenderableWidget(Button.builder(Component.literal("目标：" + selectedPlayer()), b -> {
			if (!players.isEmpty()) playerIndex = (playerIndex + 1) % players.size();
			rebuildWidgets();
		}).bounds(left + (panelWidth + 10) / 2, 97, (panelWidth - 10) / 2, 22).build());
		addRenderableWidget(Button.builder(Component.literal("提示词：" + selectedPromptLabel()), b -> {
			if (!promptIds.isEmpty()) promptIndex = (promptIndex + 1) % promptIds.size();
			rebuildWidgets();
		}).bounds(left, 129, panelWidth, 22).build());
		addRenderableWidget(Button.builder(Component.literal("应用模式并分配提示词"), b -> apply())
			.bounds(left, 171, panelWidth, 22).build());
		addRenderableWidget(Button.builder(Component.literal("刷新 AI/玩家列表"), b -> {
			if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
			status = "正在刷新服务器 AI 列表";
		}).bounds(left, 203, (panelWidth - 10) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + (panelWidth + 10) / 2, 203, (panelWidth - 10) / 2, 20).build());
	}

	private void apply() {
		try {
			if (agents.isEmpty()) throw new IllegalStateException("服务器当前没有可分配的 AI");
			if (promptIds.isEmpty()) throw new IllegalStateException("当前没有提示词预设");
			String target = requiresTarget(mode) ? selectedPlayer() : "";
			if (requiresTarget(mode) && target.equals("未选择")) {
				throw new IllegalStateException("此模式需要选择在线玩家");
			}
			UiActionClient.send("agent.mode", selectedAgent(), mode.name(), target);
			UiActionClient.send("prompt.assign", selectedAgent(), selectedPrompt());
			settings.setDefaultAgentMode(mode);
			settings.save();
			status = "已提交：" + selectedAgent() + " · " + modeLabel() + " · " + selectedPrompt();
		} catch (RuntimeException | IOException error) {
			status = "分配失败：" + error.getMessage();
		}
	}

	private String selectedAgent() { return agents.isEmpty() ? "暂无 AI" : agents.get(agentIndex).name(); }
	private String selectedPrompt() { return promptIds.isEmpty() ? "暂无预设" : promptIds.get(promptIndex); }
	private String selectedPromptLabel() { return promptLabel(selectedPrompt()); }
	private String selectedPlayer() { return players.isEmpty() ? "未选择" : players.get(playerIndex); }
	private String modeLabel() {
		return switch (mode) {
			case SURVIVAL -> "生存";
			case IDLE -> "停止";
			case HUNTER -> "追杀";
			case TEAMMATE -> "队友";
			case PVP_COACH -> "PvP 教练";
		};
	}
	private static String promptLabel(String id) {
		return switch (id) {
			case "survival" -> "生存玩家";
			case "idle" -> "空闲/通用";
			case "hunter" -> "猎人追杀";
			case "teammate" -> "队友协作";
			case "pvp_coach" -> "PvP 教练";
			case "maid" -> "AI 女仆";
			default -> "自定义 · " + id;
		};
	}
	private static boolean requiresTarget(AgentMode mode) {
		return mode == AgentMode.HUNTER || mode == AgentMode.TEAMMATE || mode == AgentMode.PVP_COACH;
	}

	@Override public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 24, 0xFFFFFFFF);
		graphics.centeredText(font, status, width / 2, 238,
			UiActionClient.lastSuccess() ? 0xFFA5D6A7 : 0xFFFF8A80);
	}
}
