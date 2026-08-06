package com.example.ai_companion.command;

import com.example.ai_companion.society.AiSocietyManager;
import com.example.ai_companion.society.SocietyJob;
import com.example.ai_companion.society.SocietyProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Administrative and inspection commands for the persistent AI society. */
public final class AiSocietyCommands {
	private AiSocietyCommands() { }

	public static void register(AiSocietyManager society) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aiplayer").then(Commands.literal("society")
				.then(Commands.literal("enroll").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().executes(c -> enroll(c.getSource(), society, name(c)))))
				.then(Commands.literal("home").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().executes(c -> home(c.getSource(), society, name(c)))))
				.then(Commands.literal("job").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().then(Commands.argument("job", StringArgumentType.word())
						.executes(c -> job(c.getSource(), society, name(c), SocietyJob.parse(StringArgumentType.getString(c, "job")))))))
				.then(Commands.literal("work").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().executes(c -> work(c.getSource(), society, name(c)))))
				.then(Commands.literal("rest").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().executes(c -> rest(c.getSource(), society, name(c)))))
				.then(Commands.literal("socialize").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(name().then(Commands.argument("other", StringArgumentType.word())
						.executes(c -> socialize(c.getSource(), society, name(c), StringArgumentType.getString(c, "other"))))))
				.then(Commands.literal("trade").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.argument("seller", StringArgumentType.word())
						.then(Commands.argument("buyer", StringArgumentType.word())
							.then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000))
								.executes(c -> trade(c.getSource(), society, StringArgumentType.getString(c, "seller"),
									StringArgumentType.getString(c, "buyer"), LongArgumentType.getLong(c, "amount")))))))
				.then(Commands.literal("status").then(name().executes(c -> status(c.getSource(), society, name(c)))))
				.then(Commands.literal("leaderboard").executes(c -> leaderboard(c.getSource(), society))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
		return Commands.argument("name", StringArgumentType.word());
	}

	private static String name(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		return StringArgumentType.getString(context, "name");
	}

	private static int enroll(CommandSourceStack source, AiSocietyManager society, String name) {
		try { return success(source, "已加入模拟社会：" + society.enroll(name).agentName()); }
		catch (Exception error) { return failure(source, error); }
	}

	private static int home(CommandSourceStack source, AiSocietyManager society, String name) {
		try {
			ServerPlayer player = source.getPlayerOrException();
			SocietyProfile profile = society.setHome(name, player.level().dimension().identifier().toString(),
				player.getX(), player.getY(), player.getZ());
			return success(source, profile.agentName() + " 的住宅已设置在当前位置");
		} catch (Exception error) { return failure(source, error); }
	}

	private static int job(CommandSourceStack source, AiSocietyManager society, String name, SocietyJob job) {
		try { society.setJob(name, job); return success(source, name + " 的工作已设为 " + job.displayName()); }
		catch (Exception error) { return failure(source, error); }
	}

	private static int work(CommandSourceStack source, AiSocietyManager society, String name) {
		try {
			SocietyProfile profile = society.work(name, System.currentTimeMillis());
			return success(source, name + " 完成一次工作，获得 " + profile.job().wage() + " 信用点，余额=" + profile.balance());
		} catch (Exception error) { return failure(source, error); }
	}

	private static int rest(CommandSourceStack source, AiSocietyManager society, String name) {
		try { society.rest(name); return success(source, name + " 已休息并恢复精力"); }
		catch (Exception error) { return failure(source, error); }
	}

	private static int socialize(CommandSourceStack source, AiSocietyManager society, String first, String second) {
		try {
			AiSocietyManager.SocialResult result = society.socialize(first, second);
			return success(source, first + " 与 " + second + " 完成社交，关系值=" + result.relationship());
		} catch (Exception error) { return failure(source, error); }
	}

	private static int trade(CommandSourceStack source, AiSocietyManager society, String seller, String buyer, long amount) {
		try { society.trade(seller, buyer, amount); return success(source, seller + " 已向 " + buyer + " 支付 " + amount + " 信用点"); }
		catch (Exception error) { return failure(source, error); }
	}

	private static int status(CommandSourceStack source, AiSocietyManager society, String name) {
		try { return success(source, describe(society.status(name))); }
		catch (Exception error) { return failure(source, error); }
	}

	private static int leaderboard(CommandSourceStack source, AiSocietyManager society) {
		var board = society.leaderboard();
		if (board.isEmpty()) return success(source, "模拟社会暂无居民");
		for (int index = 0; index < board.size(); index++) {
			SocietyProfile profile = board.get(index); int rank = index + 1;
			source.sendSuccess(() -> Component.literal("#" + rank + " " + describe(profile)), false);
		}
		return board.size();
	}

	private static String describe(SocietyProfile profile) {
		return profile.agentName() + " · 住宅=" + (profile.hasHome() ? profile.homeDimension() : "未设置")
			+ " · 工作=" + profile.job().displayName() + " · 余额=" + profile.balance()
			+ " · 精力=" + profile.energy() + " · 声望=" + profile.reputation();
	}

	private static int success(CommandSourceStack source, String message) { source.sendSuccess(() -> Component.literal(message), false); return 1; }
	private static int failure(CommandSourceStack source, Exception error) { source.sendFailure(Component.literal("模拟社会操作失败：" + error.getMessage())); return 0; }
}
