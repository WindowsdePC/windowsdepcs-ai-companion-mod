package com.example.ai_companion.agent;

/** A deliberately time-stamped, moment-in-time target observation. */
public record EyeSnapshot(String targetName, String dimension, double x, double y, double z,
						  long capturedAtTick) {
	public String promptText(long currentTick) {
		long age = Math.max(0, currentTick - capturedAtTick);
		return "天眼快照（不是实时位置）：目标=%s，维度=%s，坐标=(%.1f,%.1f,%.1f)，距采集=%d tick"
			.formatted(targetName, dimension, x, y, z, age);
	}
}
