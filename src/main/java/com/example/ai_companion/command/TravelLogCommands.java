package com.example.ai_companion.command;

import com.example.ai_companion.photo.PhotoEntry;
import com.example.ai_companion.photo.PhotographyManager;
import com.example.ai_companion.travel.TravelLogCategory;
import com.example.ai_companion.travel.TravelLogEntry;
import com.example.ai_companion.travel.TravelLogManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Registers the player-facing /aiplayer travel adventure-compendium commands. */
public final class TravelLogCommands {
	private static final int PAGE_SIZE = 8;

	private TravelLogCommands() { }

	public static void register(TravelLogManager travel, PhotographyManager photography) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var list = Commands.literal("list")
				.executes(context -> list(context.getSource(), travel, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(context -> list(context.getSource(), travel,
						IntegerArgumentType.getInteger(context, "page"))));
			var photo = Commands.literal("photo")
				.then(Commands.literal("link")
					.then(Commands.argument("entry", LongArgumentType.longArg(1))
						.then(Commands.argument("photo", LongArgumentType.longArg(1))
							.executes(context -> linkPhoto(context.getSource(), travel, photography,
								LongArgumentType.getLong(context, "entry"),
								LongArgumentType.getLong(context, "photo"))))))
				.then(Commands.literal("unlink")
					.then(Commands.argument("entry", LongArgumentType.longArg(1))
						.executes(context -> unlinkPhoto(context.getSource(), travel,
							LongArgumentType.getLong(context, "entry")))));
			var root = Commands.literal("travel")
				.then(list)
				.then(Commands.literal("stats")
					.executes(context -> stats(context.getSource(), travel)))
				.then(Commands.literal("show")
					.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(context -> show(context.getSource(), travel,
							LongArgumentType.getLong(context, "id")))))
				.then(photo);
			dispatcher.register(Commands.literal("aiplayer").then(root));
		});
	}

	private static int list(CommandSourceStack source, TravelLogManager travel, int page)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		List<TravelLogEntry> entries = travel.entries(source.getPlayerOrException());
		if (entries.isEmpty()) {
			source.sendSuccess(() -> Component.literal("旅行图鉴为空；进入新群系或地点后会自动记录"), false);
			return 0;
		}
		int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		if (page > pages) {
			source.sendFailure(Component.literal("旅行图鉴只有 " + pages + " 页"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("旅行图鉴第 " + page + "/" + pages
			+ " 页（共 " + entries.size() + " 条）："), false);
		entries.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE)
			.forEach(entry -> source.sendSuccess(() -> Component.literal(entry.displayText()), false));
		return 1;
	}

	private static int show(CommandSourceStack source, TravelLogManager travel, long id)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			TravelLogEntry entry = travel.require(source.getPlayerOrException(), id);
			source.sendSuccess(() -> Component.literal(entry.detailText()), false);
			return 1;
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal(error.getMessage()));
			return 0;
		}
	}

	private static int stats(CommandSourceStack source, TravelLogManager travel)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Map<TravelLogCategory, Long> counts = travel.categoryCounts(source.getPlayerOrException());
		String text = java.util.Arrays.stream(TravelLogCategory.values())
			.map(category -> category.displayName() + " " + counts.getOrDefault(category, 0L))
			.collect(java.util.stream.Collectors.joining(" · "));
		source.sendSuccess(() -> Component.literal("旅行图鉴统计：" + text), false);
		return 1;
	}

	private static int linkPhoto(CommandSourceStack source, TravelLogManager travel,
			PhotographyManager photography, long entryId, long photoId)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			ServerPlayer player = source.getPlayerOrException();
			PhotoEntry photo = photography.require(player, photoId);
			TravelLogEntry entry = travel.linkPhoto(player, entryId, photo);
			source.sendSuccess(() -> Component.literal("已将照片 #" + photoId
				+ " 关联到旅行日志 #" + entry.id()), false);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal("关联照片失败：" + error.getMessage()));
			return 0;
		}
	}

	private static int unlinkPhoto(CommandSourceStack source, TravelLogManager travel, long entryId)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			travel.unlinkPhoto(source.getPlayerOrException(), entryId);
			source.sendSuccess(() -> Component.literal("已取消旅行日志 #" + entryId + " 的照片关联"), false);
			return 1;
		} catch (RuntimeException | IOException error) {
			source.sendFailure(Component.literal("取消照片关联失败：" + error.getMessage()));
			return 0;
		}
	}
}
