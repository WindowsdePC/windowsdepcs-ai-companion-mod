package com.example.ai_companion.client.navigation;

import com.example.ai_companion.navigation.NavigationEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Searchable dimension/biome/structure browser inspired by compass-style navigation mods. */
public final class NavigatorScreen extends Screen {
	private final Screen parent;
	private String query = "";
	private String type = "all";
	private boolean teleport;
	private int observedRevision;

	public NavigatorScreen(Screen parent) {
		super(Component.translatable("screen.ai_companion.navigator.title"));
		this.parent = parent;
	}

	@Override protected void init() {
		observedRevision = NavigationClientController.revision();
		rebuild();
	}

	@Override public void tick() {
		super.tick();
		if (observedRevision != NavigationClientController.revision()) {
			observedRevision = NavigationClientController.revision();
			rebuild();
		}
	}

	private void rebuild() {
		clearWidgets();
		int width = Math.min(720, this.width - 30);
		int left = (this.width - width) / 2;
		EditBox search = addRenderableWidget(new EditBox(font, left, 38, width - 230, 20,
			Component.translatable("screen.ai_companion.navigator.search")));
		search.setMaxLength(120);
		search.setValue(query);
		search.setResponder(value -> query = value);
		addRenderableWidget(Button.builder(Component.literal("搜索"), button -> rebuild())
			.bounds(left + width - 220, 38, 65, 20).build());
		addRenderableWidget(Button.builder(Component.literal("类型：" + typeLabel()), button -> {
			type = switch (type) {
				case "all" -> "biome"; case "biome" -> "structure";
				case "structure" -> "dimension"; case "dimension" -> "special"; default -> "all";
			};
			rebuild();
		}).bounds(left + width - 150, 38, 150, 20).build());

		List<NavigationEntry> filtered = NavigationClientController.entries().stream()
			.filter(entry -> type.equals("all") || entry.type().equals(type))
			.filter(entry -> query.isBlank() || entry.id().toLowerCase(Locale.ROOT)
				.contains(query.strip().toLowerCase(Locale.ROOT)))
			.limit(12).toList();
		int y = 70;
		for (NavigationEntry entry : filtered) {
			String label = entryType(entry.type()) + " · " + entry.id();
			addRenderableWidget(Button.builder(Component.literal(label), button -> {
				NavigationClientController.locate(entry, teleport);
				if (minecraft != null) minecraft.setScreenAndShow(parent);
			}).bounds(left, y, width, 20).build());
			y += 23;
		}
		addRenderableWidget(Button.builder(Component.literal("模式：" + (teleport ? "传送（管理员）" : "AR 导航")),
			button -> { teleport = !teleport; rebuild(); }).bounds(left, height - 50, 190, 20).build());
		addRenderableWidget(Button.builder(Component.literal("取消当前导航"), button -> {
			NavigationHud.clear();
		}).bounds(left + 200, height - 50, 160, 20).build());
		addRenderableWidget(Button.builder(Component.literal("刷新注册表"), button -> {
			NavigationClientController.requestCatalog();
			rebuild();
		}).bounds(left + 370, height - 50, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
			.bounds(left + width - 100, height - 50, 100, 20).build());
	}

	private String typeLabel() {
		return switch (type) { case "biome" -> "群系"; case "structure" -> "结构";
			case "dimension" -> "维度"; case "special" -> "特殊"; default -> "全部"; };
	}

	private static String entryType(String type) {
		return switch (type) { case "biome" -> "群系"; case "structure" -> "结构";
			case "dimension" -> "维度"; case "special" -> "高危目标"; default -> type; };
	}

	@Override public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		String status = NavigationClientController.message();
		if (!NavigationClientController.enabled() && !NavigationClientController.waiting()) {
			status = status + "（管理员可在高级设置中开启）";
		}
		graphics.centeredText(font, status, width / 2, height - 72,
			NavigationClientController.enabled() ? 0xFFB0BEC5 : 0xFFFF8A80);
	}
}
