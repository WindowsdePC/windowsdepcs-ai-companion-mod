package com.example.ai_companion.travel;

/** Kinds of discoveries recorded in the adventure compendium. */
public enum TravelLogCategory {
	BIOME("群系"),
	VILLAGE("村庄"),
	RUIN("遗迹"),
	SPECIAL("特殊地点");

	private final String displayName;

	TravelLogCategory(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
