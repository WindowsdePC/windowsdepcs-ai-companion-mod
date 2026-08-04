package com.example.ai_companion.arena;

/** Supported AI arena team layouts. */
public enum ArenaMode {
	ONE_V_ONE("1v1", 2, 2),
	TWO_V_TWO("2v2", 4, 4),
	FREE_FOR_ALL("free-for-all", 3, 8);

	private final String id;
	private final int minimumPlayers;
	private final int maximumPlayers;

	ArenaMode(String id, int minimumPlayers, int maximumPlayers) {
		this.id = id;
		this.minimumPlayers = minimumPlayers;
		this.maximumPlayers = maximumPlayers;
	}

	public String id() {
		return id;
	}

	public void validatePlayerCount(int count) {
		if (count < minimumPlayers || count > maximumPlayers) {
			String expected = minimumPlayers == maximumPlayers
				? Integer.toString(minimumPlayers)
				: minimumPlayers + "-" + maximumPlayers;
			throw new IllegalArgumentException(id + " 模式需要 " + expected + " 个 AI");
		}
	}
}
