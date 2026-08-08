package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
import com.example.ai_companion.spyglass.SpyglassTargetCondition;
import dev.eclipseui.EclipseUI;
import dev.eclipseui.api.Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Kept isolated so Cloth-only installations never resolve EclipseUI classes. */
final class EclipseUiApiBridge {
	private EclipseUiApiBridge() {
	}

	static void verify() {
		if (EclipseUI.configScreen() == null) {
			throw new IllegalStateException("EclipseUI 配置界面 API 初始化失败");
		}
	}

	static Screen create(Screen dashboard, ClientSettings settings) {
		return EclipseUI.configScreen()
			.title(Component.literal("WindowsdePC's AI Companion Mod"))
			.parent(dashboard)
			.theme(Theme.MODERN)
			.onSave(() -> ClientSettingsSync.save(settings))
			.category(category -> category.name(Component.literal("AI系统"))
				.icon(icon("player_head"))
				.description(Component.literal("AI 管理、API 与提示词"))
				.<AgentMode>dropdown(option -> option.name("默认 AI 模式")
					.description("进入完整 AI 管理中心时默认选择的模式")
					.enumClass(AgentMode.class)
					.binding(settings::defaultAgentMode, settings::setDefaultAgentMode)
					.defaultValue(AgentMode.HUNTER))
				.label(Component.literal("保存或返回后进入完整 AI 管理中心")))
			.category(category -> category.name(Component.literal("快捷键修改"))
				.icon(icon("redstone"))
				.description(Component.literal("统一管理模组快捷键"))
				.textInput(field -> field.name(Component.literal("快捷键一"))
					.binding(() -> settings.primaryKey, value -> settings.primaryKey = value)
					.defaultValue("V").maxLength(1).validator(value -> value.matches("[A-Za-z]")))
				.textInput(field -> field.name(Component.literal("快捷键二"))
					.binding(() -> settings.secondaryKey, value -> settings.secondaryKey = value)
					.defaultValue("B").maxLength(1).validator(value -> value.matches("[A-Za-z]")))
				.textInput(field -> field.name(Component.literal("屏幕缩放快捷键"))
					.binding(() -> settings.zoomKey, value -> settings.zoomKey = value)
					.defaultValue("C").maxLength(1).validator(value -> value.matches("[A-Za-z]")))
				.textInput(field -> field.name(Component.literal("AI 控制台快捷键"))
					.binding(() -> settings.positionsKey, value -> settings.positionsKey = value)
					.defaultValue("F8").maxLength(3).validator(value -> value.toUpperCase().matches("F([1-9]|1[0-2])")))
				.textInput(field -> field.name(Component.literal("导航快捷键"))
					.binding(() -> settings.navigatorKey, value -> settings.navigatorKey = value)
					.defaultValue("G").maxLength(1).validator(value -> value.matches("[A-Za-z]")))
				.textInput(field -> field.name(Component.literal("小游戏中心快捷键"))
					.binding(() -> settings.minigameKey, value -> settings.minigameKey = value)
					.defaultValue("M").maxLength(1).validator(value -> value.matches("[A-Za-z]"))))
			.category(category -> category.name(Component.literal("游戏增强"))
				.icon(icon("golden_spear"))
				.description(Component.literal("金矛突进与服务端玩法数值"))
				.toggle(toggle -> toggle.name(Component.literal("金矛二级突进"))
					.binding(() -> settings.goldenSpearRushEnabled,
						value -> settings.goldenSpearRushEnabled = value).defaultValue(true))
				.toggle(toggle -> toggle.name(Component.literal("任意物品装备"))
					.description(Component.literal("允许所有物品进入四个装备槽；默认关闭"))
					.binding(() -> settings.flexibleEquipmentEnabled,
						value -> settings.flexibleEquipmentEnabled = value).defaultValue(false))
				.slider(slider -> slider.name(Component.literal("耐久消耗间隔"))
					.range(1, 1000, 1).bindingInt(() -> settings.durabilityEvery,
					value -> settings.durabilityEvery = value).defaultValue(15))
				.slider(slider -> slider.name(Component.literal("饥饿消耗间隔"))
					.range(1, 1000, 1).bindingInt(() -> settings.hungerEvery,
					value -> settings.hungerEvery = value).defaultValue(30))
				.slider(slider -> slider.name(Component.literal("饥饿消耗点数"))
					.range(0, 20, 1).bindingInt(() -> settings.hungerCost,
					value -> settings.hungerCost = value).defaultValue(2))
				.slider(slider -> slider.name(Component.literal("突进强度"))
					.range(0.1, 4.0, 0.01).bindingDouble(() -> settings.rushStrength,
					value -> settings.rushStrength = value).defaultValue(0.916)))
			.category(category -> category.name(Component.literal("客户端增强"))
				.icon(icon("spectral_arrow"))
				.description(Component.literal("望远镜发光与本地画面功能"))
				.toggle(toggle -> toggle.name(Component.literal("望远镜生物发光"))
					.description(Component.literal("连续观察后由服务器施加原版光灵箭发光效果"))
					.binding(() -> settings.spyglassHighlightEnabled,
						value -> settings.spyglassHighlightEnabled = value).defaultValue(true))
				.slider(slider -> slider.name(Component.literal("望远镜半径（区块）"))
					.range(1, 32, 1).bindingInt(() -> settings.spyglassRadiusChunks,
						value -> settings.spyglassRadiusChunks = value).defaultValue(10))
				.slider(slider -> slider.name(Component.literal("观察时间（秒）"))
					.range(1, 10, 1).bindingInt(() -> settings.spyglassHoldSeconds,
						value -> settings.spyglassHoldSeconds = value).defaultValue(1))
				.slider(slider -> slider.name(Component.literal("发光持续时间（秒）"))
					.range(1, 600, 1).bindingInt(() -> settings.spyglassDurationSeconds,
						value -> settings.spyglassDurationSeconds = value).defaultValue(120))
				.<SpyglassTargetCondition>dropdown(option -> option.name("发光目标条件")
					.description("全部生物、仅非玩家生物或仅敌对生物")
					.enumClass(SpyglassTargetCondition.class)
					.binding(() -> SpyglassTargetCondition.parse(settings.spyglassTargetCondition),
						value -> settings.spyglassTargetCondition = value.name())
					.defaultValue(SpyglassTargetCondition.ALL_LIVING))
				.slider(slider -> slider.name(Component.literal("触发冷却（秒）"))
					.description(Component.literal("一次发光脉冲后再次触发前的等待时间"))
					.range(1, 600, 1).bindingInt(() -> settings.spyglassCooldownSeconds,
						value -> settings.spyglassCooldownSeconds = value).defaultValue(10))
				.slider(slider -> slider.name(Component.literal("单次命中上限"))
					.description(Component.literal("目标过多时优先标记距离最近的生物"))
					.range(1, 1024, 1).bindingInt(() -> settings.spyglassMaxTargets,
						value -> settings.spyglassMaxTargets = value).defaultValue(256))
				.toggle(toggle -> toggle.name(Component.literal("按键屏幕缩放"))
					.description(Component.literal("按住设置的快捷键平滑缩放；默认关闭"))
					.binding(() -> settings.screenZoomEnabled,
						value -> settings.screenZoomEnabled = value).defaultValue(false))
				.slider(slider -> slider.name(Component.literal("缩放倍率"))
					.range(1.5, 12.0, 0.5).bindingDouble(() -> settings.zoomFactor,
					value -> settings.zoomFactor = value).defaultValue(4.0))
				.slider(slider -> slider.name(Component.literal("缩放过渡秒数"))
					.range(0.0, 1.0, 0.01).bindingDouble(() -> settings.zoomTransitionSeconds,
					value -> settings.zoomTransitionSeconds = value).defaultValue(0.18)))
			.category(category -> category.name(Component.literal("小游戏中心"))
				.icon(icon("target"))
				.description(Component.literal("贪吃蛇、俄罗斯方块、扫雷、2048 与 AI 猜拳"))
				.label(Component.literal("返回完整管理中心后可开始游戏")))
			.category(category -> category.name(Component.literal("休闲系统"))
				.icon(icon("music_disc_13")).label(Component.literal("后续功能版本开放")))
			.category(category -> category.name(Component.literal("性能优化"))
				.icon(icon("redstone_torch"))
				.description(Component.literal("限制本模组的客户端附加渲染距离"))
				.toggle(toggle -> toggle.name(Component.literal("客户端附加渲染优化"))
					.description(Component.literal("仅影响装备位附加 3D 模型；默认关闭"))
					.binding(() -> settings.clientPerformanceOptimizerEnabled,
						value -> settings.clientPerformanceOptimizerEnabled = value).defaultValue(false))
				.toggle(toggle -> toggle.name(Component.literal("自适应距离"))
					.binding(() -> settings.adaptiveExtraRenderDistance,
						value -> settings.adaptiveExtraRenderDistance = value).defaultValue(true))
				.slider(slider -> slider.name(Component.literal("目标 FPS"))
					.range(30, 240, 1).bindingInt(() -> settings.performanceTargetFps,
						value -> settings.performanceTargetFps = value).defaultValue(60))
				.slider(slider -> slider.name(Component.literal("最大附加渲染距离"))
					.range(16, 256, 8).bindingInt(() -> settings.extraRenderDistance,
						value -> settings.extraRenderDistance = value).defaultValue(96))
				.slider(slider -> slider.name(Component.literal("最小附加渲染距离"))
					.range(16, 256, 8).bindingInt(() -> settings.minimumExtraRenderDistance,
						value -> settings.minimumExtraRenderDistance = value).defaultValue(24)))
			.category(category -> category.name(Component.literal("兼容设置"))
				.icon(icon("repeater"))
				.label(Component.literal("当前使用 EclipseUI；Simple Voice Chat 为可选兼容项")))
			.category(category -> category.name(Component.literal("高级设置"))
				.icon(icon("comparator"))
				.label(Component.literal("返回后可使用完整 AI 管理、API 和提示词界面")))
			.build();
	}

	private static Identifier icon(String itemName) {
		return Identifier.withDefaultNamespace("textures/item/" + itemName + ".png");
	}
}
