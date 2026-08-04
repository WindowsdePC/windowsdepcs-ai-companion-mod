package com.example.ai_companion.arena;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable and testable team assignment for one arena battle. */
public record ArenaRoster(ArenaMode mode, List<Entry> entries) {
	public ArenaRoster {
		if (mode == null) throw new IllegalArgumentException("竞技场模式不能为空");
		entries = List.copyOf(entries);
		mode.validatePlayerCount(entries.size());
		Set<String> uniqueNames = new HashSet<>();
		for (Entry entry : entries) {
			String key = entry.name().toLowerCase(Locale.ROOT);
			if (!uniqueNames.add(key)) throw new IllegalArgumentException("参赛 AI 不能重复: " + entry.name());
		}
	}

	public static ArenaRoster create(ArenaMode mode, List<String> names) {
		mode.validatePlayerCount(names.size());
		List<Entry> entries = java.util.stream.IntStream.range(0, names.size())
			.mapToObj(index -> new Entry(validateName(names.get(index)), teamFor(mode, index)))
			.toList();
		return new ArenaRoster(mode, entries);
	}

	public Set<Integer> activeTeams(Set<String> eliminatedNames) {
		Set<String> eliminated = eliminatedNames.stream()
			.map(name -> name.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
		Set<Integer> teams = new HashSet<>();
		for (Entry entry : entries) {
			if (!eliminated.contains(entry.name().toLowerCase(Locale.ROOT))) teams.add(entry.team());
		}
		return Set.copyOf(teams);
	}

	public List<String> membersOfTeam(int team) {
		return entries.stream().filter(entry -> entry.team() == team).map(Entry::name).toList();
	}

	private static int teamFor(ArenaMode mode, int index) {
		return switch (mode) {
			case ONE_V_ONE -> index;
			case TWO_V_TWO -> index < 2 ? 0 : 1;
			case FREE_FOR_ALL -> index;
		};
	}

	private static String validateName(String name) {
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("AI 名称必须为 3-16 位英文字母、数字或下划线");
		}
		return name;
	}

	public record Entry(String name, int team) { }
}
