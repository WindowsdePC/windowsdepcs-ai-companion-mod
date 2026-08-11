package com.example.ai_companion.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Row-based shortcut editor: function | enabled | change key | reset. */
public final class ShortcutSettingsScreen extends Screen {
	private static final int PAGE_SIZE = 8;
	private final Screen parent;
	private final ClientSettings settings;
	private final List<Row> rows = new ArrayList<>();
	private int page;
	private Row capturing;
	private int combinationStage;
	private String combinationFirst;
	private String status = "点击“更改按键”，再按下新按键；修改会立即保存";

	private record Row(String id, String label, BooleanSupplier enabled, Consumer<Boolean> setEnabled,
			Supplier<String> key, Consumer<String> setKey, String defaultKey) { }

	public ShortcutSettingsScreen(Screen parent, ClientSettings settings) {
		super(Component.literal("快捷键修改"));
		this.parent = parent;
		this.settings = settings;
		buildRows();
	}

	private void buildRows() {
		rows.add(row("ui", "打开模组设置", () -> settings.uiShortcutEnabled,
			v -> settings.uiShortcutEnabled = v, () -> settings.primaryKey + "+" + settings.secondaryKey,
			ignored -> { }, "V+B"));
		rows.add(row("console", "AI 控制与消息窗口", () -> settings.agentConsoleShortcutEnabled,
			v -> settings.agentConsoleShortcutEnabled = v, () -> settings.positionsKey,
			v -> settings.positionsKey = v, "F8"));
		rows.add(row("zoom", "按住缩放", () -> settings.screenZoomEnabled,
			v -> settings.screenZoomEnabled = v, () -> settings.zoomKey,
			v -> settings.zoomKey = v, "C"));
		rows.add(row("navigator", "结构/群系导航", () -> settings.worldNavigatorEnabled,
			v -> settings.worldNavigatorEnabled = v, () -> settings.navigatorKey,
			v -> settings.navigatorKey = v, "F7"));
		rows.add(row("minigames", "小游戏中心", () -> settings.minigameShortcutEnabled,
			v -> settings.minigameShortcutEnabled = v, () -> settings.minigameMenuKey,
			v -> settings.minigameMenuKey = v, "F9"));
		rows.add(row("snake", "直接打开贪吃蛇", () -> settings.snakeShortcutEnabled,
			v -> settings.snakeShortcutEnabled = v, () -> settings.snakeShortcutKey,
			v -> settings.snakeShortcutKey = v, "KP1"));
		rows.add(row("tetris", "直接打开俄罗斯方块", () -> settings.tetrisShortcutEnabled,
			v -> settings.tetrisShortcutEnabled = v, () -> settings.tetrisShortcutKey,
			v -> settings.tetrisShortcutKey = v, "KP2"));
		rows.add(row("mines", "直接打开方块扫雷", () -> settings.minesweeperShortcutEnabled,
			v -> settings.minesweeperShortcutEnabled = v, () -> settings.minesweeperShortcutKey,
			v -> settings.minesweeperShortcutKey = v, "KP3"));
		rows.add(row("2048", "直接打开 2048", () -> settings.game2048ShortcutEnabled,
			v -> settings.game2048ShortcutEnabled = v, () -> settings.game2048ShortcutKey,
			v -> settings.game2048ShortcutKey = v, "KP4"));
		rows.add(row("rps", "直接打开 AI 猜拳", () -> settings.rockPaperScissorsShortcutEnabled,
			v -> settings.rockPaperScissorsShortcutEnabled = v, () -> settings.rockPaperScissorsShortcutKey,
			v -> settings.rockPaperScissorsShortcutKey = v, "KP5"));
		rows.add(control("up", "小游戏：向上", () -> settings.minigameUpKeyEnabled,
			v -> settings.minigameUpKeyEnabled = v, () -> settings.minigameUpKey,
			v -> settings.minigameUpKey = v, "W"));
		rows.add(control("down", "小游戏：向下", () -> settings.minigameDownKeyEnabled,
			v -> settings.minigameDownKeyEnabled = v, () -> settings.minigameDownKey,
			v -> settings.minigameDownKey = v, "S"));
		rows.add(control("left", "小游戏：向左", () -> settings.minigameLeftKeyEnabled,
			v -> settings.minigameLeftKeyEnabled = v, () -> settings.minigameLeftKey,
			v -> settings.minigameLeftKey = v, "A"));
		rows.add(control("right", "小游戏：向右", () -> settings.minigameRightKeyEnabled,
			v -> settings.minigameRightKeyEnabled = v, () -> settings.minigameRightKey,
			v -> settings.minigameRightKey = v, "D"));
		rows.add(control("action", "小游戏：动作/硬降", () -> settings.minigameActionKeyEnabled,
			v -> settings.minigameActionKeyEnabled = v, () -> settings.minigameActionKey,
			v -> settings.minigameActionKey = v, "SPACE"));
		rows.add(control("pause", "小游戏：暂停", () -> settings.minigamePauseKeyEnabled,
			v -> settings.minigamePauseKeyEnabled = v, () -> settings.minigamePauseKey,
			v -> settings.minigamePauseKey = v, "P"));
		rows.add(control("restart", "小游戏：重新开始", () -> settings.minigameRestartKeyEnabled,
			v -> settings.minigameRestartKeyEnabled = v, () -> settings.minigameRestartKey,
			v -> settings.minigameRestartKey = v, "R"));
		rows.add(control("secondary", "小游戏：插旗/撤销", () -> settings.minigameSecondaryKeyEnabled,
			v -> settings.minigameSecondaryKeyEnabled = v, () -> settings.minigameSecondaryKey,
			v -> settings.minigameSecondaryKey = v, "F"));
	}

	private static Row row(String id, String label, BooleanSupplier enabled, Consumer<Boolean> setter,
			Supplier<String> key, Consumer<String> keySetter, String defaultKey) {
		return new Row(id, label, enabled, setter, key, keySetter, defaultKey);
	}

	private static Row control(String id, String label, BooleanSupplier enabled, Consumer<Boolean> setter,
			Supplier<String> key, Consumer<String> keySetter, String defaultKey) {
		return row(id, label, enabled, setter, key, keySetter, defaultKey);
	}

	@Override protected void init() { rebuild(); }

	private void rebuild() {
		clearWidgets();
		int panelWidth = Math.min(760, width - 24);
		int left = (width - panelWidth) / 2;
		int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.clamp(page, 0, pages - 1);
		int start = page * PAGE_SIZE;
		for (int offset = 0; offset < PAGE_SIZE && start + offset < rows.size(); offset++) {
			Row row = rows.get(start + offset);
			int y = 55 + offset * 30;
			addRenderableWidget(Button.builder(Component.literal(row.enabled.getAsBoolean() ? "开启" : "关闭"),
				b -> toggle(row)).bounds(left + panelWidth - 300, y, 70, 20).build());
			addRenderableWidget(Button.builder(Component.literal(capturing == row ? "请按键…" : "更改：" + row.key.get()),
				b -> beginCapture(row)).bounds(left + panelWidth - 222, y, 132, 20).build());
			addRenderableWidget(Button.builder(Component.literal("重置"), b -> reset(row))
				.bounds(left + panelWidth - 82, y, 82, 20).build());
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; rebuild(); })
				.bounds(left, height - 29, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; rebuild(); })
				.bounds(left + 98, height - 29, 90, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + panelWidth - 90, height - 29, 90, 20).build());
	}

	private void toggle(Row row) {
		row.setEnabled.accept(!row.enabled.getAsBoolean());
		save("已" + (row.enabled.getAsBoolean() ? "开启" : "关闭") + "：“" + row.label + "”");
	}

	private void beginCapture(Row row) {
		capturing = row;
		combinationStage = 0;
		combinationFirst = null;
		status = row.id.equals("ui") ? "请依次按下两个按键（模组设置组合键）" : "请按下“" + row.label + "”的新按键";
		rebuild();
	}

	private void reset(Row row) {
		if (row.id.equals("ui")) {
			settings.primaryKey = "V";
			settings.secondaryKey = "B";
		} else row.setKey.accept(row.defaultKey);
		row.setEnabled.accept(true);
		save("已恢复默认：“" + row.label + "” = " + row.defaultKey);
	}

	@Override public boolean keyPressed(KeyEvent event) {
		if (capturing == null) return super.keyPressed(event);
		try {
			String key = ClientSettings.keyName(event.key());
			if (capturing.id.equals("ui")) {
				if (combinationStage == 0) {
					combinationFirst = key;
					combinationStage = 1;
					status = "第一个按键=" + key + "；请按第二个按键";
					return true;
				}
				if (key.equals(combinationFirst)) throw new IllegalArgumentException("组合键的两个按键不能相同");
				settings.primaryKey = combinationFirst;
				settings.secondaryKey = key;
			} else capturing.setKey.accept(key);
			String label = capturing.label;
			capturing = null;
			save("已修改：“" + label + "”");
		} catch (RuntimeException error) {
			status = "无法使用该键：" + error.getMessage();
		}
		return true;
	}

	private void save(String message) {
		try {
			settings.save();
			status = message;
		} catch (IOException error) {
			status = "保存失败：" + error.getMessage();
		}
		rebuild();
	}

	@Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
	@Override public boolean isPauseScreen() { return false; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE612171B);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelWidth = Math.min(760, width - 24);
		int left = (width - panelWidth) / 2;
		graphics.centeredText(font, "快捷键修改 · 功能名称｜开/关｜更改按键｜重置", width / 2, 18, 0xFFFFFFFF);
		int start = page * PAGE_SIZE;
		for (int offset = 0; offset < PAGE_SIZE && start + offset < rows.size(); offset++) {
			graphics.text(font, rows.get(start + offset).label, left + 8, 61 + offset * 30, 0xFFE3F2FD);
		}
		graphics.centeredText(font, status, width / 2, 33, status.contains("失败") || status.contains("无法")
			? 0xFFFF8A80 : 0xFFA5D6A7);
	}
}
