package com.example.ai_companion.furniture;

import java.util.List;

/** Stable furniture identifiers and gameplay capabilities shared by registration and tests. */
public final class FurnitureCatalog {
	public record Definition(String id, String displayName, boolean seat, int lightLevel) {
		public Definition {
			if (id == null || !id.matches("[a-z0-9_]{1,32}")) {
				throw new IllegalArgumentException("Invalid furniture id");
			}
			displayName = displayName == null ? "" : displayName.strip();
			if (displayName.isBlank() || displayName.length() > 32) {
				throw new IllegalArgumentException("Invalid furniture name");
			}
			lightLevel = Math.clamp(lightLevel, 0, 15);
		}
	}

	public static final Definition SOFA = new Definition("sofa", "沙发", true, 0);
	public static final Definition TELEVISION = new Definition("television", "电视", false, 0);
	public static final Definition COMPUTER = new Definition("computer", "电脑", false, 0);
	public static final Definition TABLE_LAMP = new Definition("table_lamp", "台灯", false, 12);
	public static final List<Definition> ALL = List.of(SOFA, TELEVISION, COMPUTER, TABLE_LAMP);

	private FurnitureCatalog() { }
}
