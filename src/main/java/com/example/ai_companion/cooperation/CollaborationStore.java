package com.example.ai_companion.cooperation;

import com.example.ai_companion.AiCompanionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON persistence for collaboration groups, proposals and votes. */
final class CollaborationStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("windowsdepcs-ai-companion-cooperation.json");
	private List<GroupData> groups = new ArrayList<>();

	static CollaborationStore load() {
		try {
			if (Files.notExists(PATH)) return new CollaborationStore();
			CollaborationStore loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
				CollaborationStore.class);
			if (loaded == null || loaded.groups == null) return new CollaborationStore();
			loaded.groups = loaded.groups.stream().map(GroupData::normalized).limit(32).toList();
			return loaded;
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with no AI collaboration groups", PATH, error);
			return new CollaborationStore();
		}
	}

	List<GroupData> entries() {
		return List.copyOf(groups);
	}

	synchronized void replace(List<GroupData> next) throws IOException {
		groups = next.stream().map(GroupData::normalized).limit(32).toList();
		Files.createDirectories(PATH.getParent());
		Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	record ProposalData(int id, String author, String text, String outcome, Map<String, Boolean> votes) {
		ProposalData normalized() {
			if (id < 1) throw new IllegalArgumentException("Invalid proposal id");
			Map<String, Boolean> safeVotes = votes == null ? Map.of() : new LinkedHashMap<>(votes);
			return new ProposalData(id, bounded(author, 16), bounded(text, 512),
				parseOutcome(outcome).name(), safeVotes);
		}
	}

	record GroupData(String id, List<String> members, String leader, String task, int nextProposalId,
			List<ProposalData> proposals, Map<String, String> leaderVotes) {
		GroupData normalized() {
			String safeId = id == null ? "" : id.strip().toLowerCase();
			if (!safeId.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid group id");
			List<String> safeMembers = members == null ? List.of() : members.stream().map(String::strip)
				.filter(value -> value.matches("[A-Za-z0-9_]{3,16}")).distinct().limit(16).toList();
			if (safeMembers.size() < 2) throw new IllegalArgumentException("A group needs at least two members");
			String safeLeader = safeMembers.stream().filter(value -> value.equalsIgnoreCase(leader)).findFirst()
				.orElse(safeMembers.getFirst());
			List<ProposalData> safeProposals = proposals == null ? List.of() : proposals.stream()
				.map(ProposalData::normalized).sorted(java.util.Comparator.comparingInt(ProposalData::id))
				.skip(Math.max(0, proposals.size() - 64L)).toList();
			Map<String, String> safeLeaderVotes = leaderVotes == null ? Map.of()
				: new LinkedHashMap<>(leaderVotes);
			return new GroupData(safeId, safeMembers, safeLeader, bounded(task, 512),
				Math.max(1, nextProposalId), safeProposals, safeLeaderVotes);
		}
	}

	private static CollaborationRules.Outcome parseOutcome(String value) {
		try { return CollaborationRules.Outcome.valueOf(value); }
		catch (RuntimeException ignored) { return CollaborationRules.Outcome.PENDING; }
	}

	private static String bounded(String value, int max) {
		String safe = value == null ? "" : value.strip();
		return safe.length() > max ? safe.substring(0, max) : safe;
	}
}
