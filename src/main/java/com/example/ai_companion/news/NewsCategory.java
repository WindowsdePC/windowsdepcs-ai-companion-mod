package com.example.ai_companion.news;

/** Event sections required by the Minecraft daily-news design. */
public enum NewsCategory {
	PLAYER("玩家事件"),
	WORLD("世界事件"),
	AI("AI事件");

	private final String displayName;

	NewsCategory(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
