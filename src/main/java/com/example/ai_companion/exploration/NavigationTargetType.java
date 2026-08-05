package com.example.ai_companion.exploration;

/** Targets supported by the biome/structure explorer compass. */
public enum NavigationTargetType {
	BIOME("biome", "群系"),
	STRUCTURE("structure", "结构"),
	BORDERLANDS("borderlands", "边境之地");

	private final String id;
	private final String label;

	NavigationTargetType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}
}
