package com.example.ai_companion.client;

import com.example.ai_companion.agent.AgentMode;
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
					.defaultValue("B").maxLength(1).validator(value -> value.matches("[A-Za-z]"))))
			.category(category -> category.name(Component.literal("游戏增强"))
				.icon(icon("golden_spear"))
				.description(Component.literal("金矛突进与服务端玩法数值"))
				.toggle(toggle -> toggle.name(Component.literal("金矛二级突进"))
					.binding(() -> settings.goldenSpearRushEnabled,
						value -> settings.goldenSpearRushEnabled = value).defaultValue(true))
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
				.description(Component.literal("只影响当前客户端的画面功能"))
				.toggle(toggle -> toggle.name(Component.literal("F3+B 使用原版发光轮廓"))
					.description(Component.literal("替代实体碰撞箱线框；仅本地生效"))
					.binding(() -> settings.f3BGlowingHitboxesEnabled,
						value -> settings.f3BGlowingHitboxesEnabled = value).defaultValue(true)))
			.category(category -> category.name(Component.literal("小游戏中心"))
				.icon(icon("target"))
				.description(Component.literal("贪吃蛇与 Minecraft 俄罗斯方块"))
				.label(Component.literal("保存或返回后，在完整管理器的小游戏中心开始游戏")))
			.category(category -> category.name(Component.literal("休闲系统"))
				.icon(icon("music_disc_13")).label(Component.literal("后续功能版本开放")))
			.category(category -> category.name(Component.literal("性能优化"))
				.icon(icon("redstone_torch")).label(Component.literal("后续功能版本开放")))
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
