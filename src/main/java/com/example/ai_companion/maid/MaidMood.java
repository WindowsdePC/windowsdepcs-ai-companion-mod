package com.example.ai_companion.maid;

/** Small, persistent mood vocabulary rendered beside an AI maid. */
public enum MaidMood {
	CALM("平静"), HAPPY("开心"), THINKING("思考中"), WORRIED("担心"), ALERT("警戒"), CONFUSED("困惑");

	private final String displayName;
	MaidMood(String displayName) { this.displayName = displayName; }
	public String displayName() { return displayName; }
}
