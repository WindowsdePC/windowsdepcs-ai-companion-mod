package com.example.ai_companion.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

		category(builder, entries, "AI系统", "保存或返回后进入完整 AI 管理中心");
		ConfigCategory shortcuts = builder.getOrCreateCategory(Component.literal("快捷键修改"));
		shortcuts.addEntry(entries.startStrField(Component.literal("快捷键一"), settings.primaryKey)
			.setDefaultValue("V").setSaveConsumer(value -> settings.primaryKey = value).build());
		shortcuts.addEntry(entries.startStrField(Component.literal("快捷键二"), settings.secondaryKey)
			.setDefaultValue("B").setSaveConsumer(value -> settings.secondaryKey = value).build());

		ConfigCategory gameplay = builder.getOrCreateCategory(Component.literal("游戏增强"));
		gameplay.addEntry(entries.startBooleanToggle(Component.literal("金矛二级突进"),
			settings.goldenSpearRushEnabled).setDefaultValue(true)
			.setSaveConsumer(value -> settings.goldenSpearRushEnabled = value).build());
		gameplay.addEntry(entries.startIntSlider(Component.literal("耐久消耗间隔"),
			settings.durabilityEvery, 1, 1000).setDefaultValue(15)
			.setSaveConsumer(value -> settings.durabilityEvery = value).build());
		gameplay.addEntry(entries.startIntSlider(Component.literal("饥饿消耗间隔"),
			settings.hungerEvery, 1, 1000).setDefaultValue(30)
			.setSaveConsumer(value -> settings.hungerEvery = value).build());

		ConfigCategory client = builder.getOrCreateCategory(Component.literal("客户端增强"));
		client.addEntry(entries.startBooleanToggle(Component.literal("F3+B 使用原版发光轮廓"),
			settings.f3BGlowingHitboxesEnabled).setDefaultValue(true)
			.setTooltip(Component.literal("替代实体碰撞箱线框；仅本地生效"))
			.setSaveConsumer(value -> settings.f3BGlowingHitboxesEnabled = value).build());

		category(builder, entries, "小游戏中心", "后续功能版本开放");
		category(builder, entries, "休闲系统", "后续功能版本开放");
		category(builder, entries, "性能优化", "后续功能版本开放");
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
}
