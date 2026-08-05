package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.exploration.NavigationMode;
import com.example.ai_companion.exploration.NavigationTargetType;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Kept isolated so EclipseUI-only installations never resolve Cloth Config classes. */
final class ClothConfigApiBridge {
	private ClothConfigApiBridge() {
	}

	static void verify() {
		if (ConfigBuilder.create() == null) {
			throw new IllegalStateException("Cloth Config 配置界面 API 初始化失败");
		}
	}

	static Screen create(Screen dashboard, ClientSettings settings) {
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(dashboard)
			.setTitle(Component.literal("WindowsdePC's AI Companion Mod"));
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory ai = builder.getOrCreateCategory(Component.literal("AI系统"));
		ai.addEntry(entries.startEnumSelector(Component.literal("默认 AI 模式"), AgentMode.class,
			settings.defaultAgentMode()).setDefaultValue(AgentMode.HUNTER)
			.setSaveConsumer(settings::setDefaultAgentMode).build());
		ai.addEntry(entries.startTextDescription(Component.literal("保存或返回后进入完整 AI 管理中心"))
			.build());

		ConfigCategory shortcuts = builder.getOrCreateCategory(Component.literal("快捷键修改"));
		shortcuts.addEntry(entries.startStrField(Component.literal("快捷键一"), settings.primaryKey)
			.setDefaultValue("V").setErrorSupplier(ClothConfigApiBridge::keyError)
			.setSaveConsumer(value -> settings.primaryKey = value).build());
		shortcuts.addEntry(entries.startStrField(Component.literal("快捷键二"), settings.secondaryKey)
			.setDefaultValue("B").setErrorSupplier(ClothConfigApiBridge::keyError)
			.setSaveConsumer(value -> settings.secondaryKey = value).build());
		shortcuts.addEntry(entries.startStrField(Component.literal("屏幕缩放快捷键"), settings.zoomKey)
			.setDefaultValue("C").setErrorSupplier(ClothConfigApiBridge::keyError)
			.setSaveConsumer(value -> settings.zoomKey = value).build());

		ConfigCategory gameplay = builder.getOrCreateCategory(Component.literal("游戏增强"));
		gameplay.addEntry(entries.startBooleanToggle(Component.literal("金矛二级突进"),
			settings.goldenSpearRushEnabled).setDefaultValue(true)
			.setSaveConsumer(value -> settings.goldenSpearRushEnabled = value).build());
		gameplay.addEntry(entries.startBooleanToggle(Component.literal("任意物品装备"),
			settings.flexibleEquipmentEnabled).setDefaultValue(false)
			.setTooltip(Component.literal("允许所有物品进入四个装备槽；默认关闭"))
			.setSaveConsumer(value -> settings.flexibleEquipmentEnabled = value).build());
		gameplay.addEntry(entries.startIntSlider(Component.literal("耐久消耗间隔"),
			settings.durabilityEvery, 1, 1000).setDefaultValue(15)
			.setSaveConsumer(value -> settings.durabilityEvery = value).build());
		gameplay.addEntry(entries.startIntSlider(Component.literal("饥饿消耗间隔"),
			settings.hungerEvery, 1, 1000).setDefaultValue(30)
			.setSaveConsumer(value -> settings.hungerEvery = value).build());
		gameplay.addEntry(entries.startIntSlider(Component.literal("饥饿消耗点数"),
			settings.hungerCost, 0, 20).setDefaultValue(2)
			.setSaveConsumer(value -> settings.hungerCost = value).build());
		gameplay.addEntry(entries.startDoubleField(Component.literal("突进强度"), settings.rushStrength)
			.setMin(0.1).setMax(4.0).setDefaultValue(0.916)
			.setSaveConsumer(value -> settings.rushStrength = value).build());

		ConfigCategory client = builder.getOrCreateCategory(Component.literal("客户端增强"));
		client.addEntry(entries.startBooleanToggle(Component.literal("F3+B 使用原版发光轮廓"),
			settings.f3BGlowingHitboxesEnabled).setDefaultValue(true)
			.setTooltip(Component.literal("替代实体碰撞箱线框；仅本地生效"))
			.setSaveConsumer(value -> settings.f3BGlowingHitboxesEnabled = value).build());
		client.addEntry(entries.startBooleanToggle(Component.literal("按键屏幕缩放"),
			settings.screenZoomEnabled).setDefaultValue(false)
			.setTooltip(Component.literal("按住设置的快捷键平滑缩放；默认关闭"))
			.setSaveConsumer(value -> settings.screenZoomEnabled = value).build());
		client.addEntry(entries.startDoubleField(Component.literal("缩放倍率"), settings.zoomFactor)
			.setMin(1.5).setMax(12.0).setDefaultValue(4.0)
			.setSaveConsumer(value -> settings.zoomFactor = value).build());
		client.addEntry(entries.startDoubleField(Component.literal("缩放过渡秒数"),
			settings.zoomTransitionSeconds).setMin(0.0).setMax(1.0).setDefaultValue(0.18)
			.setSaveConsumer(value -> settings.zoomTransitionSeconds = value).build());
		client.addEntry(entries.startBooleanToggle(Component.literal("结构群系指南针"),
			settings.explorerNavigatorEnabled).setDefaultValue(false)
			.setTooltip(Component.literal("定位群系、结构或边境之地；默认关闭"))
			.setSaveConsumer(value -> settings.explorerNavigatorEnabled = value).build());
		client.addEntry(entries.startEnumSelector(Component.literal("指南针模式"), NavigationMode.class,
			settings.explorerNavigationMode()).setDefaultValue(NavigationMode.NAVIGATE)
			.setSaveConsumer(settings::setExplorerNavigationMode).build());
		client.addEntry(entries.startEnumSelector(Component.literal("导航目标类型"), NavigationTargetType.class,
			settings.explorerTargetType()).setDefaultValue(NavigationTargetType.BIOME)
			.setSaveConsumer(settings::setExplorerTargetType).build());
		client.addEntry(entries.startStrField(Component.literal("群系/结构 ID"), settings.explorerTargetId)
			.setDefaultValue("minecraft:plains").setSaveConsumer(value -> settings.explorerTargetId = value)
			.build());
		client.addEntry(entries.startBooleanToggle(Component.literal("实验性世界限制解除"),
			settings.worldLimitsRemoved).setDefaultValue(false)
			.setTooltip(Component.literal("将边境扩至引擎硬极限并停用本模组渲染限流；默认关闭"))
			.setSaveConsumer(value -> settings.worldLimitsRemoved = value).build());
		client.addEntry(entries.startBooleanToggle(Component.literal("仁慈的虚空"),
			settings.mercifulVoidEnabled).setDefaultValue(false)
			.setTooltip(Component.literal("跌入虚空后送回高空并给予缓降；默认关闭"))
			.setSaveConsumer(value -> settings.mercifulVoidEnabled = value).build());

		category(builder, entries, "小游戏中心", "贪吃蛇、俄罗斯方块、扫雷、2048 与 AI 猜拳；返回完整管理中心后可开始游戏");
		category(builder, entries, "休闲系统", "后续功能版本开放");
		ConfigCategory performance = builder.getOrCreateCategory(Component.literal("性能优化"));
		performance.addEntry(entries.startBooleanToggle(Component.literal("客户端附加渲染优化"),
			settings.clientPerformanceOptimizerEnabled).setDefaultValue(false)
			.setTooltip(Component.literal("仅影响 F3+B 轮廓与装备位附加 3D 模型；默认关闭"))
			.setSaveConsumer(value -> settings.clientPerformanceOptimizerEnabled = value).build());
		performance.addEntry(entries.startBooleanToggle(Component.literal("自适应距离"),
			settings.adaptiveExtraRenderDistance).setDefaultValue(true)
			.setSaveConsumer(value -> settings.adaptiveExtraRenderDistance = value).build());
		performance.addEntry(entries.startIntSlider(Component.literal("目标 FPS"),
			settings.performanceTargetFps, 30, 240).setDefaultValue(60)
			.setSaveConsumer(value -> settings.performanceTargetFps = value).build());
		performance.addEntry(entries.startIntSlider(Component.literal("最大附加渲染距离"),
			settings.extraRenderDistance, 16, 256).setDefaultValue(96)
			.setSaveConsumer(value -> settings.extraRenderDistance = value).build());
		performance.addEntry(entries.startIntSlider(Component.literal("最小附加渲染距离"),
			settings.minimumExtraRenderDistance, 16, 256).setDefaultValue(24)
			.setSaveConsumer(value -> settings.minimumExtraRenderDistance = value).build());
		category(builder, entries, "兼容设置", "当前使用 Cloth Config；Simple Voice Chat 为可选兼容项");
		category(builder, entries, "高级设置", "返回后可使用完整 AI 管理、API 和提示词界面");
		builder.setSavingRunnable(() -> ClientSettingsSync.save(settings));
		return builder.build();
	}

	private static void category(ConfigBuilder builder, ConfigEntryBuilder entries,
			String name, String description) {
		builder.getOrCreateCategory(Component.literal(name))
			.addEntry(entries.startTextDescription(Component.literal(description)).build());
	}

	private static Optional<Component> keyError(String value) {
		return value != null && value.matches("[A-Za-z]")
			? Optional.empty() : Optional.of(Component.literal("只支持一个 A-Z 字母"));
	}
}
