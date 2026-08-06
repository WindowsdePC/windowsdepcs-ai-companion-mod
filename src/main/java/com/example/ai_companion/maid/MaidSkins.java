package com.example.ai_companion.maid;

import java.util.List;

/** Built-in skin identifiers are the uploaded filenames without the .png suffix. */
public final class MaidSkins {
	public static final List<String> DEFAULTS = List.of(
		"1000030746", "1000030745", "1000030742", "1000030741",
		"1000030733", "1000030744", "1000030743");
	private static final String KEY_PATTERN = "[a-zA-Z0-9_./-]{1,96}";

	private MaidSkins() { }

	public static String validate(String key) {
		String value = key == null ? "" : key.strip();
		if (!value.matches(KEY_PATTERN)) throw new IllegalArgumentException("皮肤标识无效");
		return value;
	}

	public static String validateOptional(String key) {
		String value = key == null || key.equalsIgnoreCase("none") ? "" : key.strip();
		return value.isEmpty() ? "" : validate(value);
	}
}
