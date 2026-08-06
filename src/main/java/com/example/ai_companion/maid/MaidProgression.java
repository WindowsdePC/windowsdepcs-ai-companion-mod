package com.example.ai_companion.maid;

/** Pure rules for the third maid progression revision. */
public final class MaidProgression {
	public static final int MAX_LEVEL = 30;
	public static final int MAX_WORK_EXPERIENCE = 1_000_000_000;
	public static final double BASE_MAX_HEALTH = 20.0;
	public static final double HEALTH_PER_LEVEL = 2.0;
	public static final double MAX_HEALTH = 80.0;

	private MaidProgression() { }

	public static int workExperienceCost(int currentLevel) {
		validateUpgradeable(currentLevel);
		return 40 + currentLevel * 20;
	}

	/** Number of displayed player levels requested for the next maid level. */
	public static int playerLevelCost(int currentLevel) {
		validateUpgradeable(currentLevel);
		return 1 + currentLevel / 5;
	}

	/**
	 * Converts a displayed level cost to the points used by levels 0..N. For example, four
	 * levels cost 40 points (7+9+11+13), regardless of the player's current displayed level.
	 */
	public static int frontLevelPointCost(int displayedLevels) {
		if (displayedLevels < 0 || displayedLevels > 255) {
			throw new IllegalArgumentException("等级消耗必须在 0-255 之间");
		}
		if (displayedLevels <= 16) return displayedLevels * displayedLevels + 6 * displayedLevels;
		if (displayedLevels <= 31) {
			return (int) Math.floor(2.5 * displayedLevels * displayedLevels
				- 40.5 * displayedLevels + 360.0);
		}
		return (int) Math.floor(4.5 * displayedLevels * displayedLevels
			- 162.5 * displayedLevels + 2220.0);
	}

	public static double maxHealth(int level) {
		return Math.min(MAX_HEALTH, BASE_MAX_HEALTH + Math.clamp(level, 0, MAX_LEVEL) * HEALTH_PER_LEVEL);
	}

	public static int workExperienceForAction(String action) {
		return switch (action == null ? "" : action) {
			case "move" -> 4;
			case "say" -> 2;
			case "wait" -> 1;
			default -> 1;
		};
	}

	private static void validateUpgradeable(int currentLevel) {
		if (currentLevel < 0 || currentLevel >= MAX_LEVEL) {
			throw new IllegalArgumentException("女仆等级已达到上限或无效");
		}
	}
}
