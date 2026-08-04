package com.example.ai_companion.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.example.ai_companion.photo.PhotoEntry;
import com.example.ai_companion.photo.PhotographyManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;

/** Registers the player-facing /aiplayer album command tree. */
public final class PhotographyCommands {
	private static final int PAGE_SIZE = 8;

	private PhotographyCommands() {}

	public static void register(PhotographyManager photography) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var list = Commands.literal("list")
				.executes(c -> list(c.getSource(), photography, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(c -> list(c.getSource(), photography,
						IntegerArgumentType.getInteger(c, "page"))));
			var album = Commands.literal("album")
				.then(list)
				.then(Commands.literal("show")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(c -> show(c.getSource(), photography,
							LongArgumentType.getLong(c, "id")))))
				.then(Commands.literal("caption")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.then(Commands.argument("text", StringArgumentType.greedyString())
							.executes(c -> caption(c.getSource(), photography,
								LongArgumentType.getLong(c, "id"),
								StringArgumentType.getString(c, "text"))))))
				.then(Commands.literal("review")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(c -> review(c.getSource(), photography,
							LongArgumentType.getLong(c, "id")))))
				.then(Commands.literal("delete")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(c -> delete(c.getSource(), photography,
							LongArgumentType.getLong(c, "id")))));
			dispatcher.register(Commands.literal("aiplayer").then(album));
		});
	}

	private static int list(CommandSourceStack source, PhotographyManager photography, int page)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		List<PhotoEntry> photos = photography.photos(source.getPlayerOrException());
		if (photos.isEmpty()) {
			source.sendSuccess(() -> Component.literal("相册为空；手持相机右键即可拍摄"), false);
			return 0;
		}
		int pages = Math.max(1, (photos.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		if (page > pages) {
			source.sendFailure(Component.literal("相册只有 " + pages + " 页"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("相册第 " + page + "/" + pages + " 页（共 "
			+ photos.size() + " 张）："), false);
		photos.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE)
			.forEach(photo -> source.sendSuccess(() -> Component.literal(photo.displayText()), false));
		return 1;
	}

	private static int show(CommandSourceStack source, PhotographyManager photography, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			PhotoEntry photo = photography.require(source.getPlayerOrException(), id);
			source.sendSuccess(() -> Component.literal(photo.displayText() + "\n场景：" + photo.sceneSummary()
				+ "\n视角：yaw " + photo.yaw() + "，pitch " + photo.pitch()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int caption(CommandSourceStack source, PhotographyManager photography, long id, String text)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			PhotoEntry photo = photography.caption(source.getPlayerOrException(), id, text);
			source.sendSuccess(() -> Component.literal("已更新照片 #" + photo.id() + " 的说明"), false);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal("更新说明失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int review(CommandSourceStack source, PhotographyManager photography, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			ServerPlayer player = source.getPlayerOrException();
			photography.review(source.getServer(), player, id);
			source.sendSuccess(() -> Component.literal("AI 正在评价照片 #" + id + "……"), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("请求评价失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int delete(CommandSourceStack source, PhotographyManager photography, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			if (!photography.delete(source.getPlayerOrException(), id)) {
				source.sendFailure(Component.literal("找不到照片 #" + id));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("已删除照片 #" + id), false);
			return 1;
		} catch (IOException error) {
			source.sendFailure(Component.literal("删除照片失败：" + error.getMessage()));
			return 0;
		}
	}
}
