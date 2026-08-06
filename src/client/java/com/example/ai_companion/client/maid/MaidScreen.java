package com.example.ai_companion.client.maid;

import com.example.ai_companion.maid.MaidSkins;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Focused maid creation/chat page opened from the unified AI system. */
public final class MaidScreen extends Screen {
	private final Screen parent;
	private String name = "Maid_01";
	private int skinIndex;
	private String skinKey = MaidSkins.DEFAULTS.getFirst();
	private String capeKey = "";
	private String message = "请介绍自己，并告诉我你现在的心情";
	private String status = "选择皮肤、可选披风，然后召唤 AI 女仆";

	public MaidScreen(Screen parent) {
		super(Component.literal("AI 女仆"));
		this.parent = parent;
	}

	@Override protected void init() {
		int left = width / 2 - 245;
		EditBox nameBox = addRenderableWidget(new EditBox(font, left, 54, 210, 20,
			Component.literal("女仆名字")));
		nameBox.setMaxLength(16);
		nameBox.setValue(name);
		nameBox.setResponder(value -> name = value);

		addRenderableWidget(Button.builder(Component.literal("皮肤：" + skinKey), button -> cycleSkin())
			.bounds(left, 88, 210, 20).build());
		addRenderableWidget(Button.builder(Component.literal("打开 LittleSkin"), button ->
			Util.getPlatform().openUri("https://littleskin.cn"))
			.bounds(left + 220, 88, 160, 20).build());
		addRenderableWidget(Button.builder(Component.literal("召唤"), button -> summon())
			.bounds(left + 390, 88, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("＋ 没有自己心仪的皮肤？点击添加本地文件"),
			button -> importTexture(false)).bounds(left, 122, 380, 20).build());
		addRenderableWidget(Button.builder(Component.literal(capeKey.isBlank()
			? "＋ 添加本地披风（没有预设披风）" : "披风：" + capeKey),
			button -> importTexture(true)).bounds(left, 150, 380, 20).build());

		EditBox chat = addRenderableWidget(new EditBox(font, left, 203, 380, 20,
			Component.literal("文字指令")));
		chat.setMaxLength(500);
		chat.setValue(message);
		chat.setResponder(value -> message = value);
		addRenderableWidget(Button.builder(Component.literal("发送给女仆"), button -> chat())
			.bounds(left + 390, 203, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
			.bounds(left + 390, height - 34, 100, 20).build());
	}

	private void cycleSkin() {
		skinIndex = Math.floorMod(skinIndex + 1, MaidSkins.DEFAULTS.size());
		skinKey = MaidSkins.DEFAULTS.get(skinIndex);
		clearWidgets();
		init();
	}

	private void summon() {
		try {
			validateName();
			sendCommand("aimaid summon " + name + " " + skinKey + " "
				+ (capeKey.isBlank() ? "none" : capeKey));
			status = "已请求召唤 " + name + "；皮肤=" + skinKey;
		} catch (RuntimeException error) { status = "召唤失败：" + error.getMessage(); }
	}

	private void chat() {
		try {
			validateName();
			if (message.isBlank()) throw new IllegalArgumentException("指令不能为空");
			sendCommand("aimaid chat " + name + " " + message);
			status = name + " 正在思考；心情会显示在她头顶的对话标记中";
		} catch (RuntimeException error) { status = "发送失败：" + error.getMessage(); }
	}

	private void importTexture(boolean cape) {
		Frame frame = new Frame();
		try {
			FileDialog dialog = new FileDialog(frame, cape ? "选择披风 PNG" : "选择皮肤 PNG",
				FileDialog.LOAD);
			dialog.setFile("*.png");
			dialog.setVisible(true);
			if (dialog.getFile() == null) return;
			Path source = Path.of(dialog.getDirectory(), dialog.getFile());
			var image = ImageIO.read(source.toFile());
			if (image == null || image.getWidth() > 1024 || image.getHeight() > 1024) {
				throw new IllegalArgumentException("请选择不超过 1024×1024 的 PNG");
			}
			if (!cape && !((image.getWidth() == 64 || image.getWidth() == 128)
					&& (image.getHeight() == 64 || image.getHeight() == 128))) {
				throw new IllegalArgumentException("皮肤应为 64×64 或 128×128 PNG");
			}
			byte[] bytes = Files.readAllBytes(source);
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			String key = "custom/" + hash;
			Path directory = FabricLoader.getInstance().getConfigDir()
				.resolve("windowsdepcs-ai-companion-maid-textures");
			Files.createDirectories(directory);
			Path target = directory.resolve(hash + ".png");
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			MaidClientRegistry.registerCustom(key, target);
			if (cape) capeKey = key; else skinKey = key;
			status = "已导入本地" + (cape ? "披风" : "皮肤") + "：" + source.getFileName();
			clearWidgets();
			init();
		} catch (Exception error) {
			status = "导入失败：" + error.getMessage();
		} finally { frame.dispose(); }
	}

	private void validateName() {
		if (!name.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("名字必须为 3-16 位英文字母、数字或下划线");
		}
	}

	private void sendCommand(String command) {
		if (minecraft == null || minecraft.getConnection() == null) throw new IllegalStateException("未连接服务器");
		minecraft.getConnection().sendCommand(command);
	}

	@Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
		int left = width / 2 - 245;
		graphics.text(font, "女仆名字", left, 40, 0xFFA0A0A0);
		graphics.text(font, "文字聊天 / AI 指令", left, 189, 0xFFA0A0A0);
		graphics.text(font, "默认皮肤名称来自上传文件名；括号内容不会进入名称。", left, 176, 0xFFB0BEC5);
		graphics.text(font, status, left, height - 31, status.contains("失败") ? 0xFFFF7777 : 0xFFA8E6A3);
	}

	@Override public boolean isPauseScreen() { return false; }
}
