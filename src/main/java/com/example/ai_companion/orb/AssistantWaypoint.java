package com.example.ai_companion.orb;

import java.util.Locale;

/** One persistent coordinate saved by a player's assistant orb. */
public record AssistantWaypoint(String name, String dimension, double x, double y, double z,
		long createdAtEpochMillis) {
	public AssistantWaypoint {
		if (name == null || !name.matches("[A-Za-z0-9_\\-\\u4e00-\\u9fff]{1,32}")) {
			throw new IllegalArgumentException("坐标名称必须为 1-32 位中英文、数字、下划线或连字符");
		}
		if (dimension == null || dimension.isBlank() || dimension.length() > 128) {
			throw new IllegalArgumentException("维度标识无效");
		}
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("坐标必须是有限数值");
		}
		if (createdAtEpochMillis < 0) throw new IllegalArgumentException("保存时间无效");
	}

	public String displayText() {
		return String.format(Locale.ROOT, "%s · %s · X %.1f, Y %.1f, Z %.1f", name, dimension, x, y, z);
	}
}
