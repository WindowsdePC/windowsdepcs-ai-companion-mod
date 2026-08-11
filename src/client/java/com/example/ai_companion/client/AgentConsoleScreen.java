package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** F8 popup that keeps AI positions, replies and conversation inside the mod UI. */
public final class AgentConsoleScreen extends Screen {
	private final Screen parent;
	private final ClientSettings settings;
	private String selectedAgent = "";
	private String instruction = "";
	private long positionsRevision = -1;
	private EditBox instructionBox;

	public AgentConsoleScreen(Screen parent, ClientSettings settings) {
		super(Component.literal("AI 控制与消息"));
		this.parent = parent;
		this.settings = settings;
	}

	@Override protected void init() {
		buildWidgets();
		if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
	}

	private void buildWidgets() {
		int panelWidth = Math.min(760, width - 30);
		int left = (width - panelWidth) / 2;
		List<AgentPosition> values = AgentPositionHud.snapshot();
		int y = 54;
		for (AgentPosition value : values.stream().limit(8).toList()) {
			boolean selected = value.name().equalsIgnoreCase(selectedAgent);
			addRenderableWidget(Button.builder(Component.literal((selected ? "▶ " : "") + value.displayText()), b -> {
				selectedAgent = value.name();
				rebuildWidgets();
			}).bounds(left, y, panelWidth, 20).build());
			y += 22;
		}
		if (values.isEmpty()) y += 22;
		instructionBox = addRenderableWidget(new EditBox(font, left, Math.max(238, y + 8),
			panelWidth - 190, 22, Component.literal("直接发送给所选 AI")));
		instructionBox.setMaxLength(500);
		instructionBox.setValue(instruction);
		instructionBox.setResponder(value -> instruction = value);
		addRenderableWidget(Button.builder(Component.literal("发送到本窗口"), b -> send())
			.bounds(left + panelWidth - 180, Math.max(238, y + 8), 180, 22).build());
		addRenderableWidget(Button.builder(Component.literal("刷新"), b -> {
			if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
			UiActionClient.note("正在刷新 AI 状态");
		}).bounds(left, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("提示词分配"), b -> {
			if (minecraft != null) minecraft.setScreenAndShow(new PromptAssignmentScreen(this,
				com.example.ai_companion.config.PromptStore.loadClient(), settings));
		}).bounds(left + 110, height - 28, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal("语音状态"), b -> {
			if (selectedAgent.isBlank()) UiActionClient.note("请先选择 AI");
			else UiActionClient.send("agent.voice_status", selectedAgent);
		}).bounds(left + 260, height - 28, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("实体详情/传送"), b -> {
			if (minecraft != null) minecraft.setScreenAndShow(new AgentListScreen(this));
		}).bounds(left + 390, height - 28, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
			.bounds(left + panelWidth - 100, height - 28, 100, 20).build());
	}

	private void send() {
		try {
			if (selectedAgent.isBlank()) throw new IllegalStateException("请先选择 AI");
			if (instruction.isBlank()) throw new IllegalStateException("请输入消息或任务");
			UiActionClient.send("agent.ask", selectedAgent, instruction.strip());
			UiActionClient.note("你 → " + selectedAgent + "：" + instruction.strip());
			instruction = "";
			instructionBox.setValue("");
		} catch (RuntimeException error) {
			UiActionClient.note("发送失败：" + error.getMessage());
		}
	}

	@Override public void tick() {
		super.tick();
		if (positionsRevision != AgentPositionHud.revision()) {
			positionsRevision = AgentPositionHud.revision();
			rebuildWidgets();
		}
	}

	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == settings.positionsCode()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override public boolean isPauseScreen() { return false; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		int panelWidth = Math.min(760, width - 30);
		int left = (width - panelWidth) / 2;
		graphics.fill(left - 6, 18, left + panelWidth + 6, height - 34, 0xB018222C);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "F8 · AI 控制与消息（不写入聊天栏）", width / 2, 26, 0xFFFFFFFF);
		List<AgentPosition> values = AgentPositionHud.snapshot();
		if (values.isEmpty()) graphics.centeredText(font, "当前没有 AI，或正在从服务器刷新", width / 2, 72, 0xFFB0BEC5);
		int messageY = Math.max(278, 70 + Math.min(8, values.size()) * 22);
		graphics.text(font, "本窗口消息：", left + 4, messageY, 0xFFFFD54F);
		List<UiActionClient.Message> history = UiActionClient.messages();
		int start = Math.max(0, history.size() - 8);
		for (int index = start; index < history.size(); index++) {
			UiActionClient.Message message = history.get(index);
			graphics.text(font, message.text(), left + 10, messageY + 15 + (index - start) * 13,
				message.success() ? 0xFFE3F2FD : 0xFFFF8A80);
		}
		AgentPosition selected = values.stream().filter(value -> value.name().equalsIgnoreCase(selectedAgent))
			.findFirst().orElse(null);
		if (selected != null && !selected.lastMessage().isBlank()) {
			graphics.text(font, selected.name() + " 最近状态：" + selected.lastMessage(), left + 4,
				Math.min(height - 44, messageY + 122), 0xFFA5D6A7);
		}
	}
}
