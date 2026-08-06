package com.example.ai_companion.command;

import com.example.ai_companion.society.SocietyJob;
import com.example.ai_companion.society.SocietyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Commands for the bounded first version of AI life simulation. */
public final class SocietyCommands {
	private SocietyCommands() {}

	public static void register(SocietyManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("aisociety")
				.then(Commands.literal("list").executes(c -> list(c.getSource(), manager)))
				.then(Commands.literal("status").then(Commands.argument("agent", StringArgumentType.word())
					.executes(c -> status(c.getSource(), manager, StringArgumentType.getString(c, "agent")))))
				.then(Commands.literal("enroll").requires(SocietyCommands::admin)
					.then(Commands.argument("agent", StringArgumentType.word()).executes(c -> enroll(c.getSource(), manager,
						StringArgumentType.getString(c, "agent")))))
				.then(Commands.literal("home").requires(SocietyCommands::admin)
					.then(Commands.argument("agent", StringArgumentType.word()).executes(c -> home(c.getSource(), manager,
						StringArgumentType.getString(c, "agent")))))
				.then(Commands.literal("job").requires(SocietyCommands::admin)
					.then(Commands.argument("agent", StringArgumentType.word())
						.then(Commands.argument("job", StringArgumentType.word()).executes(c -> job(c.getSource(), manager,
							StringArgumentType.getString(c, "agent"), StringArgumentType.getString(c, "job"))))))
				.then(Commands.literal("work").requires(SocietyCommands::admin)
					.then(Commands.argument("agent", StringArgumentType.word()).executes(c -> work(c.getSource(), manager,
						StringArgumentType.getString(c, "agent")))))
				.then(Commands.literal("social").requires(SocietyCommands::admin)
					.then(Commands.argument("first", StringArgumentType.word())
						.then(Commands.argument("second", StringArgumentType.word()).executes(c -> social(c.getSource(), manager,
							StringArgumentType.getString(c, "first"), StringArgumentType.getString(c, "second"))))))));
	}

	private static boolean admin(CommandSourceStack source) { return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN); }
	private static int list(CommandSourceStack source, SocietyManager manager) {
		var residents = manager.residents();
		if (residents.isEmpty()) { source.sendSuccess(() -> Component.literal("AI 社会目前没有成员"), false); return 0; }
		residents.forEach(resident -> source.sendSuccess(() -> Component.literal(resident.displayText()), false)); return residents.size();
	}
	private static int status(CommandSourceStack source, SocietyManager manager, String agent) {
		try { source.sendSuccess(() -> Component.literal(manager.status(agent).displayText()), false); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int enroll(CommandSourceStack source, SocietyManager manager, String agent) {
		try { var resident = manager.enroll(agent); source.sendSuccess(() -> Component.literal("已登记：" + resident.displayText()), true); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int home(CommandSourceStack source, SocietyManager manager, String agent) {
		try { var player = source.getPlayerOrException(); var resident = manager.setHome(agent,
			player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ());
			source.sendSuccess(() -> Component.literal("住所已设置：" + resident.displayText()), true); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int job(CommandSourceStack source, SocietyManager manager, String agent, String job) {
		try { var resident = manager.setJob(agent, SocietyJob.parse(job)); source.sendSuccess(() -> Component.literal("职业已设置：" + resident.displayText()), true); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int work(CommandSourceStack source, SocietyManager manager, String agent) {
		try { var resident = manager.work(agent, System.currentTimeMillis()); source.sendSuccess(() -> Component.literal("工作完成：" + resident.displayText()), true); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int social(CommandSourceStack source, SocietyManager manager, String first, String second) {
		try { int relation = manager.socialize(first, second); source.sendSuccess(() -> Component.literal(first + " 与 " + second + " 的关系值为 " + relation), true); return 1; }
		catch (Exception error) { return fail(source, error); }
	}
	private static int fail(CommandSourceStack source, Exception error) {
		source.sendFailure(Component.literal(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())); return 0;
	}
}
