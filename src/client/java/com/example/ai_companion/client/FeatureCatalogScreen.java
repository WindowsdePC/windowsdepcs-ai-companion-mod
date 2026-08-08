package com.example.ai_companion.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Discoverable catalogue for every completed feature.
 *
 * <p>Feature cards always open a real detail page. Server queries use the typed UI channel and
 * render their result in that page; they never paste commands into chat.</p>
 */
public final class FeatureCatalogScreen extends Screen {
	public enum Group {
		ALL("全部功能"), LEISURE("休闲系统"), COMPATIBILITY("兼容与状态");

		private final String label;
		Group(String label) { this.label = label; }
	}

	public enum Feature {
		AI_PLAYER(Group.ALL, "AI 玩家系统", "生成、选择、模式、目标、提示词、天眼与任务控制。",
			new Action("检查服务端能力", "feature.status")),
		MAID(Group.ALL, "AI 女仆", "召唤、皮肤/披风、对话、心情、背包、成长和所有权。"),
		COLLABORATION(Group.ALL, "多 AI 协作", "共享任务、提案投票、共识与领队选举。",
			new Action("检查服务端能力", "feature.status")),
		ARENA(Group.ALL, "AI 竞技场", "1v1、2v2 与混战，自动恢复参赛者状态。",
			new Action("刷新比赛状态", "arena.status")),
		PET(Group.ALL, "AI 宠物竞技", "创建、训练、竞速、战斗与排行榜。",
			new Action("我的宠物", "pet.list"), new Action("排行榜", "pet.leaderboard")),
		MINIGAMES(Group.ALL, "五个本地小游戏", "贪吃蛇、俄罗斯方块、扫雷、2048 与 AI 猜拳。"),
		GOLDEN_SPEAR(Group.ALL, "金矛二级突进", "开关、耐久间隔、饥饿消耗与突进强度均可修改。",
			new Action("检查服务端能力", "feature.status")),
		FLEXIBLE_EQUIPMENT(Group.ALL, "任意物品装备", "安全切换装备兼容并可打乱非快捷栏物品。",
			new Action("检查服务端能力", "feature.status")),
		SPYGLASS(Group.ALL, "望远镜生物发光", "范围、观察时间、持续、冷却、目标类型和上限。",
			new Action("检查服务端能力", "feature.status")),
		NAVIGATION(Group.ALL, "结构/群系/维度导航", "动态目录、搜索、AR 方向、进度与跨维度提示。",
			new Action("检查服务端能力", "feature.status")),
		WORLD_SAFETY(Group.ALL, "世界安全增强", "仁慈虚空与原版最大世界边界开关。",
			new Action("检查服务端能力", "feature.status")),
		PERFORMANCE(Group.ALL, "客户端性能优化", "本模组附加模型渲染距离、目标 FPS 与自适应距离。"),
		PHOTO(Group.LEISURE, "AI 摄影与相册", "浏览照片记录、场景元数据和 AI 评价入口。",
			new Action("刷新相册", "album.list")),
		TRAVEL(Group.LEISURE, "旅行日志与图鉴", "群系、维度、村庄、结构发现记录与照片关联。",
			new Action("刷新图鉴统计", "travel.stats")),
		NEWS(Group.LEISURE, "Minecraft 日报", "世界、玩家与 AI 事件归档及 AI 编辑版。",
			new Action("生成/刷新今日日报", "news.today")),
		LIVESTREAM(Group.LEISURE, "AI 直播", "AI 观看者、事实约束弹幕和间隔控制。",
			new Action("刷新直播状态", "live.status")),
		FURNITURE(Group.LEISURE, "家具休闲", "沙发、电视、电脑、台灯及 AI 入座聊天。",
			new Action("检查服务端能力", "feature.status")),
		MUSIC(Group.LEISURE, "AI 音乐合奏", "和声、回声、低音三种编排与音符盒跟奏。",
			new Action("刷新合奏状态", "music.status")),
		SOCIETY(Group.LEISURE, "AI 模拟社会", "住宅、职业、工作、交易、关系与排行榜。",
			new Action("刷新社会排行榜", "society.leaderboard")),
		WEATHER(Group.LEISURE, "世界自然事件", "极光、流星雨、沙尘暴、雷暴、预报与日程。",
			new Action("刷新事件状态", "weather.status"), new Action("查看自动配置", "weather.config")),
		ORB(Group.LEISURE, "AI 助手球", "聊天、提醒、坐标收藏和附近坐标探索。",
			new Action("刷新探索摘要", "orb.explore")),
		API(Group.COMPATIBILITY, "AI API 与模型", "服务器权威保存接口、模型和令牌配置状态。",
			new Action("同步服务器配置", "config.status")),
		UI_BACKEND(Group.COMPATIBILITY, "双 UI 后端", "EclipseUI 优先，Cloth Config 备用；所有功能共用同一目录。"),
		OPTIONAL_MODS(Group.COMPATIBILITY, "可选模组兼容", "Mod Menu、Simple Voice Chat、背包与整理模组兼容。",
			new Action("检查服务端能力", "feature.status"));

		private final Group group;
		private final String title;
		private final String description;
		private final List<Action> actions;

		Feature(Group group, String title, String description, Action... actions) {
			this.group = group;
			this.title = title;
			this.description = description;
			this.actions = List.of(actions);
		}
	}

	private record Action(String label, String action) { }

	private static final int PAGE_SIZE = 8;
	private final Screen parent;
	private final Group group;
	private Feature selected;
	private int page;
	private long resultRevision = UiActionClient.revision();
	private String status = "选择功能卡片进入独立界面；服务器结果只显示在模组 UI 内";

	public FeatureCatalogScreen(Screen parent, Group group) {
		super(Component.literal("功能目录 · " + group.label));
		this.parent = parent;
		this.group = group;
	}

	public FeatureCatalogScreen(Screen parent, Feature feature) {
		this(parent, feature.group);
		this.selected = feature;
	}

	@Override protected void init() { rebuild(); }

	@Override public void tick() {
		super.tick();
		if (resultRevision != UiActionClient.revision()) {
			resultRevision = UiActionClient.revision();
			status = UiActionClient.lastMessage();
		}
	}

	private List<Feature> features() {
		return Arrays.stream(Feature.values())
			.filter(value -> group == Group.ALL || value.group == group).toList();
	}

	private void rebuild() {
		clearWidgets();
		if (selected == null) buildCatalog();
		else buildDetail();
	}

	private void buildCatalog() {
		List<Feature> values = features();
		int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.clamp(page, 0, pages - 1);
		int panelWidth = Math.min(760, width - 28);
		int left = (width - panelWidth) / 2;
		int cardWidth = (panelWidth - 12) / 2;
		int start = page * PAGE_SIZE;
		for (int offset = 0; offset < PAGE_SIZE && start + offset < values.size(); offset++) {
			Feature feature = values.get(start + offset);
			int column = offset % 2;
			int row = offset / 2;
			addRenderableWidget(Button.builder(Component.literal("进入 · " + feature.title), button -> {
				selected = feature;
				rebuild();
			}).bounds(left + column * (cardWidth + 12), 52 + row * 48, cardWidth, 24).build());
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; rebuild(); })
				.bounds(left, height - 53, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; rebuild(); })
				.bounds(left + 100, height - 53, 90, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
			.bounds(left + panelWidth - 100, height - 28, 100, 20).build());
	}

	private void buildDetail() {
		int panelWidth = Math.min(760, width - 28);
		int left = (width - panelWidth) / 2;
		int actionWidth = selected.actions.isEmpty() ? panelWidth
			: Math.min(220, (panelWidth - Math.max(0, selected.actions.size() - 1) * 8)
			/ selected.actions.size());
		int x = left;
		for (Action action : selected.actions) {
			addRenderableWidget(Button.builder(Component.literal(action.label), b -> request(action))
				.bounds(x, 104, actionWidth, 22).build());
			x += actionWidth + 8;
		}
		addRenderableWidget(Button.builder(Component.literal("返回功能目录"), b -> {
			selected = null;
			rebuild();
		}).bounds(left, height - 28, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
			.bounds(left + panelWidth - 100, height - 28, 100, 20).build());
	}

	private void request(Action action) {
		try {
			UiActionClient.send(action.action);
			status = "正在刷新：" + action.label;
		} catch (RuntimeException error) {
			status = "操作失败：" + error.getMessage();
		}
	}

	@Override public void onClose() {
		if (minecraft != null) minecraft.setScreenAndShow(parent);
	}

	@Override public boolean isPauseScreen() { return false; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xEA10161C);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelWidth = Math.min(760, width - 28);
		int left = (width - panelWidth) / 2;
		graphics.centeredText(font, selected == null ? "全部已完成功能 · " + group.label : selected.title,
			width / 2, 20, 0xFFFFFFFF);
		if (selected == null) {
			List<Feature> values = features();
			int start = page * PAGE_SIZE;
			int cardWidth = (panelWidth - 12) / 2;
			for (int offset = 0; offset < PAGE_SIZE && start + offset < values.size(); offset++) {
				Feature feature = values.get(start + offset);
				int column = offset % 2;
				int row = offset / 2;
				graphics.text(font, feature.description, left + column * (cardWidth + 12) + 4,
					79 + row * 48, 0xFFB0BEC5);
			}
		} else {
			graphics.centeredText(font, selected.description, width / 2, 58, 0xFFB0BEC5);
			graphics.text(font, "操作结果（保留在本窗口）：", left, 150, 0xFFFFD54F);
			List<UiActionClient.Message> messages = UiActionClient.messages();
			int start = Math.max(0, messages.size() - 10);
			for (int index = start; index < messages.size(); index++) {
				UiActionClient.Message message = messages.get(index);
				graphics.text(font, trim(message.text(), 112), left + 8, 170 + (index - start) * 15,
					message.success() ? 0xFFE3F2FD : 0xFFFF8A80);
			}
		}
		graphics.text(font, trim(status, 120), left, height - 48,
			status.contains("失败") ? 0xFFFF8A80 : 0xFFA5D6A7);
	}

	private static String trim(String value, int maximum) {
		if (value == null) return "";
		return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
	}
}
