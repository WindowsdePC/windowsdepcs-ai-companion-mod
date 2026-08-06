package com.example.ai_companion.command;

import com.example.ai_companion.music.AiMusicManager;
import com.example.ai_companion.music.MusicSession;
import com.example.ai_companion.music.MusicStyle;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Player-facing controls for note-block performances with AI accompaniment. */
public final class AiMusicCommands {
	private AiMusicCommands() { }

	public static void register(AiMusicManager music) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer").then(Commands.literal("music")
				.then(Commands.literal("start")
					.then(Commands.argument("aiNames", StringArgumentType.word())
						.executes(c -> start(c.getSource(), music,
							StringArgumentType.getString(c, "aiNames"), MusicStyle.HARMONY))
						.then(Commands.argument("style", StringArgumentType.word())
							.executes(c -> start(c.getSource(), music,
								StringArgumentType.getString(c, "aiNames"),
								MusicStyle.parse(StringArgumentType.getString(c, "style")))))))
				.then(Commands.literal("style").then(Commands.argument("style", StringArgumentType.word())
					.executes(c -> style(c.getSource(), music,
						MusicStyle.parse(StringArgumentType.getString(c, "style"))))))
				.then(Commands.literal("status").executes(c -> status(c.getSource(), music)))
				.then(Commands.literal("stop").executes(c -> stop(c.getSource(), music))))));
	}

	private static int start(CommandSourceStack source, AiMusicManager music, String names, MusicStyle style)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			List<String> members = Arrays.stream(names.split(",")).map(String::strip)
				.filter(value -> !value.isBlank()).toList();
			MusicSession session = music.start(source.getPlayerOrException(), members, style);
			source.sendSuccess(() -> Component.literal("AI 合奏已开始：" + String.join(", ", session.members())
				+ " · 风格=" + session.style().displayName()
				+ "。现在左键敲击音符盒即可演奏，AI 会在身边加入合奏。"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("开始 AI 合奏失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int style(CommandSourceStack source, AiMusicManager music, MusicStyle value)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			music.setStyle(source.getPlayerOrException(), value);
			source.sendSuccess(() -> Component.literal("AI 合奏风格已改为：" + value.displayName()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("修改合奏风格失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int status(CommandSourceStack source, AiMusicManager music)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			MusicSession session = music.status(source.getPlayerOrException());
			source.sendSuccess(() -> Component.literal("AI 合奏：成员=" + String.join(", ", session.members())
				+ " · 风格=" + session.style().displayName() + " · 已跟随音符=" + session.notesPlayed()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int stop(CommandSourceStack source, AiMusicManager music)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!music.stop(source.getPlayerOrException())) {
			source.sendFailure(Component.literal("你还没有进行中的 AI 合奏"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("AI 合奏已结束"), false);
		return 1;
	}
}
