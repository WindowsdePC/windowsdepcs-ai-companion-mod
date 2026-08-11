package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Server-authoritative AI list with a real detail panel and per-entity teleport action. */
public final class AgentListScreen extends Screen {
	private final Screen parent;
	private String selectedName = "";
	private long positionsRevision = -1L;
	private int page;
	private static final int PAGE_SIZE = 7;

	public AgentListScreen(Screen parent) {
		super(Component.literal("AI 模式与实体详情"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		buildWidgets();
		if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
	}

	private void buildWidgets() {
		int panelWidth = Math.min(820, width - 28);
		int left = (width - panelWidth) / 2;
		int listWidth = Math.min(330, panelWidth / 2 - 8);
		List<AgentPosition> values = AgentPositionHud.snapshot();
		int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.clamp(page, 0, pages - 1);
		int start = page * PAGE_SIZE;
		for (int offset = 0; offset < PAGE_SIZE && start + offset < values.size(); offset++) {
			AgentPosition value = values.get(start + offset);
			boolean selected = value.name().equalsIgnoreCase(selectedName);
			addRenderableWidget(Button.builder(Component.literal((selected ? "▶ " : "") + value.name()
				+ " · " + modeLabel(value)), button -> {
				selectedName = value.name();
				rebuildWidgets();
			}).bounds(left, 54 + offset * 24, listWidth, 20).build());
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; rebuildWidgets(); })
				.bounds(left, 228, 92, 20).build());
			addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; rebuildWidgets(); })
				.bounds(left + 100, 228, 92, 20).build());
		}
		AgentPosition selected = selected(values);
		if (selected != null) {
			addRenderableWidget(Button.builder(Component.literal("传送到该 AI（管理员）"), b ->
				UiActionClient.send("agent.teleport_to", selected.name()))
				.bounds(left + listWidth + 18, 228, Math.min(210, panelWidth - listWidth - 18), 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("刷新实体详情"), b -> {
			if (minecraft != null) AgentPositionHud.requestRefresh(minecraft);
		}).bounds(left, height - 28, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + panelWidth - 100, height - 28, 100, 20).build());
	}

	@Override
	public void tick() {
		super.tick();
		if (positionsRevision != AgentPositionHud.revision()) {
			positionsRevision = AgentPositionHud.revision();
			List<AgentPosition> values = AgentPositionHud.snapshot();
			if (selected(values) == null && !values.isEmpty()) selectedName = values.getFirst().name();
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xED10171D);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelWidth = Math.min(820, width - 28);
		int left = (width - panelWidth) / 2;
		int listWidth = Math.min(330, panelWidth / 2 - 8);
		int detailX = left + listWidth + 18;
		graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
		graphics.text(font, "点击左侧 AI 后在右侧查看详情", left, 39, 0xFFB0BEC5);
		List<AgentPosition> values = AgentPositionHud.snapshot();
		if (values.isEmpty()) {
			graphics.text(font, "当前没有已生成且存活的 AI 实体", left, 66, 0xFFFFAB91);
			return;
		}
		AgentPosition value = selected(values);
		if (value == null) return;
		int y = 54;
		String[] lines = {
			"名称：" + value.name(),
			"UUID：" + value.uuid(),
			"维度：" + value.dimension(),
			String.format(Locale.ROOT, "坐标：X %.1f  Y %.1f  Z %.1f", value.x(), value.y(), value.z()),
			String.format(Locale.ROOT, "生命：%.1f / %.1f", value.health(), value.maxHealth()),
			"实际游戏模式：" + value.gameMode(),
			"AI 行为模式：" + modeLabel(value),
			"目标：" + blank(value.targetName()),
			"提示词：" + blank(value.promptId()),
			"自动决策：" + (value.automaticEnabled() ? "已开启" : "已关闭"),
			"持续任务：" + blank(value.activeTask()),
			"最近状态：" + blank(value.lastMessage())
		};
		for (String line : lines) {
			graphics.text(font, trim(line, 76), detailX, y, 0xFFE3F2FD);
			y += 14;
		}
	}

	private AgentPosition selected(List<AgentPosition> values) {
		return values.stream().filter(value -> value.name().equalsIgnoreCase(selectedName))
			.findFirst().orElse(null);
	}

	private static String modeLabel(AgentPosition value) {
		return switch (value.mode()) {
			case SURVIVAL -> "生存";
			case HUNTER -> "追杀";
			case TEAMMATE -> "队友/跟随";
			case PVP_COACH -> "PvP 教练";
			case IDLE -> "空闲";
		};
	}

	private static String blank(String value) { return value == null || value.isBlank() ? "无" : value; }
	private static String trim(String value, int maximum) {
		return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
	}

	@Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
	@Override public boolean isPauseScreen() { return false; }
}
