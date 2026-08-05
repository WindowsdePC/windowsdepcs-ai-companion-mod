package com.example.ai_companion.cooperation;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.agent.AgentManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persistent shared tasks, consensus proposals and leader elections for AI groups. */
public final class CollaborationManager implements AutoCloseable {
	private static final int MAX_GROUPS = 32;

	private static final class Proposal {
		final int id;
		final String author;
		final String text;
		final Map<String, Boolean> votes = new LinkedHashMap<>();
		CollaborationRules.Outcome outcome;

		Proposal(int id, String author, String text, CollaborationRules.Outcome outcome,
				Map<String, Boolean> votes) {
			this.id = id;
			this.author = author;
			this.text = text;
			this.outcome = outcome;
			this.votes.putAll(votes);
		}
	}

	private static final class Group {
		final String id;
		final List<String> members;
		final List<Proposal> proposals = new ArrayList<>();
		final Map<String, String> leaderVotes = new LinkedHashMap<>();
		String leader;
		String task;
		int nextProposalId;

		Group(CollaborationStore.GroupData data) {
			id = data.id();
			members = new ArrayList<>(data.members());
			leader = data.leader();
			task = data.task();
			nextProposalId = data.nextProposalId();
			for (Map.Entry<String, String> vote : data.leaderVotes().entrySet()) {
				String voter = canonicalMember(vote.getKey());
				String candidate = canonicalMember(vote.getValue());
				if (voter != null && candidate != null) leaderVotes.put(voter.toLowerCase(Locale.ROOT), candidate);
			}
			for (CollaborationStore.ProposalData proposal : data.proposals()) {
				Map<String, Boolean> validVotes = new LinkedHashMap<>();
				for (Map.Entry<String, Boolean> vote : proposal.votes().entrySet()) {
					String voter = canonicalMember(vote.getKey());
					if (voter != null) validVotes.put(voter.toLowerCase(Locale.ROOT), vote.getValue());
				}
				proposals.add(new Proposal(proposal.id(), proposal.author(), proposal.text(),
					CollaborationRules.proposalOutcome(members.size(), validVotes), validVotes));
			}
		}

		private String canonicalMember(String name) {
			return members.stream().filter(member -> member.equalsIgnoreCase(name)).findFirst().orElse(null);
		}
	}

	public record ProposalView(int id, String author, String text, CollaborationRules.Outcome outcome,
			int approvals, int rejections) {
		public String displayText() {
			return "#%d [%s] %s：%s（赞成 %d / 反对 %d）".formatted(id, outcome, author, text,
				approvals, rejections);
		}
	}

	public record GroupView(String id, List<String> members, String leader, String task,
			List<ProposalView> proposals) {
		public String displayText() {
			return "%s · 领队=%s · 成员=%s · 任务=%s".formatted(id, leader,
				String.join(",", members), task.isBlank() ? "未设置" : task);
		}
	}

	private final AgentManager agents;
	private final CollaborationStore store = CollaborationStore.load();
	private final Map<String, Group> groups = new LinkedHashMap<>();

	public CollaborationManager(AgentManager agents) {
		this.agents = agents;
		for (CollaborationStore.GroupData data : store.entries()) groups.put(data.id(), new Group(data));
	}

	public synchronized GroupView create(String id, String memberList) {
		String key = normalizeId(id);
		if (groups.containsKey(key)) throw new IllegalArgumentException("协作组已存在: " + key);
		if (groups.size() >= MAX_GROUPS) throw new IllegalStateException("协作组数量已达上限 " + MAX_GROUPS);
		List<String> members = java.util.Arrays.stream(memberList.split("[,，]"))
			.map(String::strip).filter(value -> !value.isBlank()).map(agents::canonicalName).distinct().toList();
		if (members.size() < 2 || members.size() > 16) throw new IllegalArgumentException("成员数量必须为 2-16");
		Group group = new Group(new CollaborationStore.GroupData(key, members, members.getFirst(), "", 1,
			List.of(), Map.of()).normalized());
		groups.put(key, group);
		save();
		return view(group);
	}

	public synchronized boolean remove(String id) {
		boolean removed = groups.remove(normalizeId(id)) != null;
		if (removed) save();
		return removed;
	}

	public synchronized GroupView setTask(String id, String task) {
		Group group = requireGroup(id);
		String safe = bounded(task, 512);
		if (safe.isBlank()) throw new IllegalArgumentException("共享任务不能为空");
		group.task = safe;
		save();
		return view(group);
	}

	public synchronized ProposalView propose(String id, String author, String text) {
		Group group = requireGroup(id);
		String member = requireMember(group, author);
		String safe = bounded(text, 512);
		if (safe.isBlank()) throw new IllegalArgumentException("提案不能为空");
		Proposal proposal = new Proposal(group.nextProposalId++, member, safe,
			CollaborationRules.Outcome.PENDING, Map.of());
		group.proposals.add(proposal);
		if (group.proposals.size() > 64) group.proposals.removeFirst();
		save();
		return proposalView(proposal);
	}

	public synchronized ProposalView vote(String id, int proposalId, String voter, boolean approve) {
		Group group = requireGroup(id);
		String member = requireMember(group, voter);
		Proposal proposal = group.proposals.stream().filter(value -> value.id == proposalId).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("找不到提案 #" + proposalId));
		if (proposal.outcome != CollaborationRules.Outcome.PENDING) {
			throw new IllegalStateException("该提案已经结束投票: " + proposal.outcome);
		}
		proposal.votes.put(member.toLowerCase(Locale.ROOT), approve);
		proposal.outcome = CollaborationRules.proposalOutcome(group.members.size(), proposal.votes);
		save();
		return proposalView(proposal);
	}

	public synchronized GroupView voteLeader(String id, String voter, String candidate) {
		Group group = requireGroup(id);
		String safeVoter = requireMember(group, voter);
		String safeCandidate = requireMember(group, candidate);
		group.leaderVotes.put(safeVoter.toLowerCase(Locale.ROOT), safeCandidate);
		String elected = CollaborationRules.electedLeader(group.members.size(), group.leaderVotes);
		if (!elected.isBlank()) {
			group.leader = elected;
			group.leaderVotes.clear();
		}
		save();
		return view(group);
	}

	public synchronized GroupView view(String id) {
		return view(requireGroup(id));
	}

	public synchronized Collection<GroupView> views() {
		return groups.values().stream().map(this::view).toList();
	}

	/** Returns bounded server-authoritative group context for one AI decision request. */
	public synchronized String promptContext(String agentName) {
		List<String> contexts = new ArrayList<>();
		for (Group group : groups.values()) {
			if (group.members.stream().noneMatch(member -> member.equalsIgnoreCase(agentName))) continue;
			String consensus = group.proposals.reversed().stream()
				.filter(value -> value.outcome == CollaborationRules.Outcome.APPROVED).findFirst()
				.map(value -> value.text).orElse("无");
			contexts.add("协作组=%s，领队=%s，共享任务=%s，最近共识=%s".formatted(group.id, group.leader,
				group.task.isBlank() ? "未设置" : group.task, consensus));
			if (contexts.size() == 3) break;
		}
		return bounded(String.join("；", contexts), 1_500);
	}

	private GroupView view(Group group) {
		return new GroupView(group.id, List.copyOf(group.members), group.leader, group.task,
			group.proposals.stream().map(CollaborationManager::proposalView).toList());
	}

	private static ProposalView proposalView(Proposal proposal) {
		int approvals = (int) proposal.votes.values().stream().filter(Boolean::booleanValue).count();
		return new ProposalView(proposal.id, proposal.author, proposal.text, proposal.outcome, approvals,
			proposal.votes.size() - approvals);
	}

	private Group requireGroup(String id) {
		Group group = groups.get(normalizeId(id));
		if (group == null) throw new IllegalArgumentException("找不到协作组: " + id);
		return group;
	}

	private static String requireMember(Group group, String name) {
		return group.members.stream().filter(member -> member.equalsIgnoreCase(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(name + " 不是协作组成员"));
	}

	private void save() {
		try {
			store.replace(groups.values().stream().map(group -> new CollaborationStore.GroupData(group.id,
				List.copyOf(group.members), group.leader, group.task, group.nextProposalId,
				group.proposals.stream().map(proposal -> new CollaborationStore.ProposalData(proposal.id,
					proposal.author, proposal.text, proposal.outcome.name(), Map.copyOf(proposal.votes))).toList(),
				Map.copyOf(group.leaderVotes))).toList());
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save AI collaboration groups", error);
		}
	}

	private static String normalizeId(String id) {
		String safe = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
		if (!safe.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("协作组 ID 必须为 1-32 位小写字母、数字、_ 或 -");
		return safe;
	}

	private static String bounded(String value, int max) {
		String safe = value == null ? "" : value.strip();
		return safe.length() > max ? safe.substring(0, max) : safe;
	}

	@Override
	public synchronized void close() {
		save();
	}
}
