package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** F8 console: AI list, direct requests and replies rendered inside one popup. */
public final class AgentConsoleScreen extends Screen {
	private final Screen parent;
	private String selectedAgent = "";
	private String instruction = "报告你当前的状态并决定下一步";
	private long positionRevision = -1;
	private long messageRevision = UiActionClient.revision();

	public AgentConsoleScreen(Screen parent) {
		super(Component.literal("AI 控制台"));
		this.parent = parent;
	}

	@Override protected void init() {
		rebuild();
		AgentPositionHud.requestRefresh(minecraft);
	}

	@Override public void tick() {
		super.tick();
		if (positionRevision != AgentPositionHud.revision()) {
			positionRevision = AgentPositionHud.revision();
			if (selectedAgent.isBlank() && !AgentPositionHud.snapshot().isEmpty()) {
				selectedAgent = AgentPositionHud.snapshot().getFirst().name();
			}
			rebuild();
		}
		messageRevision = UiActionClient.revision();
	}

	private void rebuild() {
		clearWidgets();
		int panelWidth = Math.min(760, width - 30);
		int left = (width - panelWidth) / 2;
		List<AgentPosition> positions = AgentPositionHud.snapshot();
		int y = 48;
		for (AgentPosition position : positions.stream().limit(8).toList()) {
			addRenderableWidget(Button.builder(Component.literal(position.displayText()), button -> {
				selectedAgent = position.name();
				rebuild();
			}).bounds(left, y, panelWidth - 140, 20).build());
			addRenderableWidget(Button.builder(Component.literal("传送至 " + position.name()), button ->
				UiActionClient.send("agent.teleport_to", position.name()))
				.bounds(left + panelWidth - 132, y, 132, 20).build());
			y += 23;
		}
		EditBox agent = addRenderableWidget(new EditBox(font, left, Math.max(y + 8, 238), 150, 20,
			Component.literal("AI 名称")));
		agent.setMaxLength(16);
		agent.setValue(selectedAgent);
		agent.setResponder(value -> selectedAgent = value);
		EditBox prompt = addRenderableWidget(new EditBox(font, left + 160, Math.max(y + 8, 238),
			panelWidth - 280, 20, Component.literal("直接发送给 AI 的内容")));
		prompt.setMaxLength(500);
		prompt.setValue(instruction);
		prompt.setResponder(value -> instruction = value);
		addRenderableWidget(Button.builder(Component.literal("发送给 AI"), button -> send())
			.bounds(left + panelWidth - 110, Math.max(y + 8, 238), 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("刷新"), button -> AgentPositionHud.requestRefresh(minecraft))
			.bounds(left, height - 28, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("关闭（F8）"), button -> onClose())
			.bounds(left + panelWidth - 110, height - 28, 110, 20).build());
	}

	private void send() {
		if (selectedAgent.isBlank() || instruction.isBlank()) return;
		UiActionClient.send("agent.ask", selectedAgent.strip(), instruction.strip());
	}

	@Override public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelWidth = Math.min(760, width - 30);
		int left = (width - panelWidth) / 2;
		graphics.centeredText(font, "F8 AI 控制台 · 所有服务器返回都显示在本窗口，不写入聊天栏", width / 2, 16, 0xFFFFFFFF);
		if (AgentPositionHud.waiting()) graphics.text(font, "正在刷新 AI 列表…", left, 34, 0xFFB0BEC5);
		else if (!AgentPositionHud.error().isBlank()) graphics.text(font, AgentPositionHud.error(), left, 34, 0xFFFF8A80);
		else if (AgentPositionHud.snapshot().isEmpty()) graphics.text(font, "当前没有已加载的 AI", left, 34, 0xFFB0BEC5);
		int y = Math.max(274, Math.min(height - 112, 306));
		graphics.text(font, "窗口消息：", left, y, 0xFFFFD54F);
		List<UiActionClient.Message> messages = UiActionClient.messagesAfter(Math.max(0, messageRevision - 8));
		for (UiActionClient.Message message : messages.stream().skip(Math.max(0, messages.size() - 6)).toList()) {
			y += 13;
			graphics.text(font, (message.success() ? "✓ " : "✗ ") + message.text(), left, y,
				message.success() ? 0xFFA5D6A7 : 0xFFFF8A80);
		}
	}
}
