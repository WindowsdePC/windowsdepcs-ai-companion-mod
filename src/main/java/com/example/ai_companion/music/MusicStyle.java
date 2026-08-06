package com.example.ai_companion.music;

import java.util.Locale;

/** Bounded accompaniment patterns used by AI ensemble members. */
public enum MusicStyle {
	HARMONY("harmony", "和声") {
		@Override public int noteFor(int input, int memberIndex) {
			return fold(input + switch (memberIndex % 3) { case 1 -> 4; case 2 -> 7; default -> 0; });
		}
		@Override public int delayFor(int memberIndex) { return 2; }
	},
	ECHO("echo", "回声") {
		@Override public int noteFor(int input, int memberIndex) { return input; }
		@Override public int delayFor(int memberIndex) { return 4 + memberIndex * 2; }
	},
	BASS("bass", "低音") {
		@Override public int noteFor(int input, int memberIndex) {
			return fold(memberIndex % 2 == 0 ? input - 12 : input - 5);
		}
		@Override public int delayFor(int memberIndex) { return memberIndex % 2 == 0 ? 2 : 5; }
	};

	private final String id;
	private final String displayName;

	MusicStyle(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() { return id; }
	public String displayName() { return displayName; }
	public abstract int noteFor(int input, int memberIndex);
	public abstract int delayFor(int memberIndex);

	public static MusicStyle parse(String value) {
		String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
		for (MusicStyle style : values()) {
			if (style.id.equals(normalized)) return style;
		}
		throw new IllegalArgumentException("未知合奏风格：" + value + "（可用 harmony、echo、bass）");
	}

	/** Note blocks accept pitches from 0 through 24; fold transposed notes into that range. */
	static int fold(int note) {
		int result = note;
		while (result < 0) result += 12;
		while (result > 24) result -= 12;
		return Math.clamp(result, 0, 24);
	}
}
