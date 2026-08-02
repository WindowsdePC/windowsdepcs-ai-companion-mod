package com.example.ai_companion.client;

import dev.eclipseui.EclipseUI;
import dev.eclipseui.api.Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
				.description(Component.literal("AI 管理、API 与提示词"))
				.label(Component.literal("保存或返回后进入完整 AI 管理中心")))
			.category(category -> category.name(Component.literal("快捷键修改"))
				.textInput(field -> field.name(Component.literal("快捷键一"))
					.binding(() -> settings.primaryKey, value -> settings.primaryKey = value).defaultValue("V"))
				.textInput(field -> field.name(Component.literal("快捷键二"))
					.binding(() -> settings.secondaryKey, value -> settings.secondaryKey = value).defaultValue("B")))
			.category(category -> category.name(Component.literal("游戏增强"))
				.toggle(toggle -> toggle.name(Component.literal("金矛二级突进"))
					.binding(() -> settings.goldenSpearRushEnabled, value -> settings.goldenSpearRushEnabled = value)
					.defaultValue(true))
				.slider(slider -> slider.name(Component.literal("耐久消耗间隔"))
					.range(1, 1000, 1).bindingInt(() -> settings.durabilityEvery,
					value -> settings.durabilityEvery = value).defaultValue(15))
				.slider(slider -> slider.name(Component.literal("饥饿消耗间隔"))
					.range(1, 1000, 1).bindingInt(() -> settings.hungerEvery,
					value -> settings.hungerEvery = value).defaultValue(30)))
			.category(category -> category.name(Component.literal("客户端增强"))
				.toggle(toggle -> toggle.name(Component.literal("F3+B 使用原版发光轮廓"))
					.description(Component.literal("替代实体碰撞箱线框；仅本地生效"))
					.binding(() -> settings.f3BGlowingHitboxesEnabled,
						value -> settings.f3BGlowingHitboxesEnabled = value).defaultValue(true)))
			.category(category -> category.name(Component.literal("小游戏中心"))
				.label(Component.literal("后续功能版本开放")))
			.category(category -> category.name(Component.literal("休闲系统"))
				.label(Component.literal("后续功能版本开放")))
			.category(category -> category.name(Component.literal("性能优化"))
				.label(Component.literal("后续功能版本开放")))
			.category(category -> category.name(Component.literal("兼容设置"))
				.label(Component.literal("当前使用 EclipseUI；Simple Voice Chat 为可选兼容项")))
			.category(category -> category.name(Component.literal("高级设置"))
				.label(Component.literal("返回后可使用完整 AI 管理、API 和提示词界面")))
			.build();
	}
}
