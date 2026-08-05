package com.example.ai_companion.command;

import com.example.ai_companion.cooperation.CollaborationManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Administrative commands for persistent multi-AI collaboration groups. */
public final class CollaborationCommands {
	private CollaborationCommands() {}

	public static void register(CollaborationManager manager) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
			dispatcher.register(Commands.literal("aicoop")
				.then(Commands.literal("status")
					.executes(c -> statusAll(c.getSource(), manager))
					.then(Commands.argument("group", StringArgumentType.word())
						.executes(c -> status(c.getSource(), manager,
							StringArgumentType.getString(c, "group")))))
				.then(Commands.literal("create").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.then(Commands.argument("members", StringArgumentType.greedyString())
							.executes(c -> create(c.getSource(), manager,
								StringArgumentType.getString(c, "group"),
								StringArgumentType.getString(c, "members"))))))
				.then(Commands.literal("remove").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.executes(c -> remove(c.getSource(), manager,
							StringArgumentType.getString(c, "group")))))
				.then(Commands.literal("task").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.then(Commands.argument("task", StringArgumentType.greedyString())
							.executes(c -> task(c.getSource(), manager,
								StringArgumentType.getString(c, "group"),
								StringArgumentType.getString(c, "task"))))))
				.then(Commands.literal("propose").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.then(Commands.argument("agent", StringArgumentType.word())
							.then(Commands.argument("proposal", StringArgumentType.greedyString())
								.executes(c -> propose(c.getSource(), manager,
									StringArgumentType.getString(c, "group"),
									StringArgumentType.getString(c, "agent"),
									StringArgumentType.getString(c, "proposal")))))))
				.then(Commands.literal("vote").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.then(Commands.argument("proposalId", IntegerArgumentType.integer(1))
							.then(Commands.argument("agent", StringArgumentType.word())
								.then(Commands.argument("approve", BoolArgumentType.bool())
									.executes(c -> vote(c.getSource(), manager,
										StringArgumentType.getString(c, "group"),
										IntegerArgumentType.getInteger(c, "proposalId"),
										StringArgumentType.getString(c, "agent"),
										BoolArgumentType.getBool(c, "approve"))))))))
				.then(Commands.literal("leader-vote").requires(CollaborationCommands::admin)
					.then(Commands.argument("group", StringArgumentType.word())
						.then(Commands.argument("voter", StringArgumentType.word())
							.then(Commands.argument("candidate", StringArgumentType.word())
								.executes(c -> leaderVote(c.getSource(), manager,
									StringArgumentType.getString(c, "group"),
									StringArgumentType.getString(c, "voter"),
									StringArgumentType.getString(c, "candidate")))))))));
	}

	private static boolean admin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
	}

	private static int statusAll(CommandSourceStack source, CollaborationManager manager) {
		var groups = manager.views();
		if (groups.isEmpty()) {
			source.sendSuccess(() -> Component.literal("当前没有 AI 协作组"), false);
			return 0;
		}
		groups.forEach(group -> source.sendSuccess(() -> Component.literal(group.displayText()), false));
		return groups.size();
	}

	private static int status(CommandSourceStack source, CollaborationManager manager, String id) {
		try {
			var group = manager.view(id);
			source.sendSuccess(() -> Component.literal(group.displayText()), false);
			group.proposals().stream().skip(Math.max(0, group.proposals().size() - 10L))
				.forEach(proposal -> source.sendSuccess(() -> Component.literal(proposal.displayText()), false));
			return 1;
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int create(CommandSourceStack source, CollaborationManager manager, String id,
			String members) {
		try {
			var group = manager.create(id, members);
			source.sendSuccess(() -> Component.literal("已创建 " + group.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int remove(CommandSourceStack source, CollaborationManager manager, String id) {
		if (!manager.remove(id)) {
			source.sendFailure(Component.literal("找不到协作组: " + id));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("已删除协作组: " + id), true);
		return 1;
	}

	private static int task(CommandSourceStack source, CollaborationManager manager, String id, String task) {
		try {
			var group = manager.setTask(id, task);
			source.sendSuccess(() -> Component.literal("已更新 " + group.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int propose(CommandSourceStack source, CollaborationManager manager, String id,
			String agent, String text) {
		try {
			var proposal = manager.propose(id, agent, text);
			source.sendSuccess(() -> Component.literal("已创建 " + proposal.displayText()), true);
			return proposal.id();
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int vote(CommandSourceStack source, CollaborationManager manager, String id,
			int proposalId, String agent, boolean approve) {
		try {
			var proposal = manager.vote(id, proposalId, agent, approve);
			source.sendSuccess(() -> Component.literal(proposal.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int leaderVote(CommandSourceStack source, CollaborationManager manager, String id,
			String voter, String candidate) {
		try {
			var group = manager.voteLeader(id, voter, candidate);
			source.sendSuccess(() -> Component.literal("领队投票已记录；" + group.displayText()), true);
			return 1;
		} catch (RuntimeException error) {
			return fail(source, error);
		}
	}

	private static int fail(CommandSourceStack source, RuntimeException error) {
		source.sendFailure(Component.literal(error.getMessage()));
		return 0;
	}
}
