package com.example.ai_companion.weather;

/** One bounded history entry recorded when a natural event starts. */
public record WeatherEventRecord(WeatherEventType type, long startedAtEpochMillis,
	int plannedDurationSeconds, boolean automatic) {
	public WeatherEventRecord {
		if (type == null) throw new IllegalArgumentException("事件类型不能为空");
		if (startedAtEpochMillis < 0) throw new IllegalArgumentException("开始时间无效");
		if (plannedDurationSeconds < 60 || plannedDurationSeconds > 1800)
			throw new IllegalArgumentException("计划时长必须为 60～1800 秒");
	}
}
