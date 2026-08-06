package com.example.ai_companion.music;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** One bounded, server-authoritative player/AI ensemble session. */
public record MusicSession(UUID ownerId, String ownerName, List<String> members, MusicStyle style,
		long startedAtTick, long lastNoteTick, long notesPlayed) {
	public static final int MAX_MEMBERS = 8;

	public MusicSession {
		if (ownerId == null) throw new IllegalArgumentException("缺少演奏者 UUID");
		ownerName = bounded(ownerName, 16);
		if (ownerName.isBlank()) throw new IllegalArgumentException("缺少演奏者名称");
		if (style == null) throw new IllegalArgumentException("缺少合奏风格");
		members = members == null ? List.of() : members.stream().map(value -> bounded(value, 16))
			.filter(value -> value.matches("[A-Za-z0-9_]{3,16}"))
			.limit(MAX_MEMBERS).toList();
		if (members.isEmpty()) throw new IllegalArgumentException("至少需要 1 名 AI 参加合奏");
		Set<String> unique = members.stream().map(value -> value.toLowerCase(Locale.ROOT))
			.collect(Collectors.toSet());
		if (unique.size() != members.size()) throw new IllegalArgumentException("合奏 AI 不能重复");
		startedAtTick = Math.max(0, startedAtTick);
		lastNoteTick = Math.max(startedAtTick, lastNoteTick);
		notesPlayed = Math.max(0, notesPlayed);
	}

	public MusicSession afterPlayerNote(long tick) {
		return new MusicSession(ownerId, ownerName, members, style, startedAtTick,
			Math.max(lastNoteTick, tick), notesPlayed + 1);
	}

	public MusicSession withStyle(MusicStyle value) {
		return new MusicSession(ownerId, ownerName, members, value, startedAtTick, lastNoteTick, notesPlayed);
	}

	private static String bounded(String value, int max) {
		String safe = value == null ? "" : value.strip();
		return safe.length() > max ? safe.substring(0, max) : safe;
	}
}
