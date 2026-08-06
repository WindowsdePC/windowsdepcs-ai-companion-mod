package com.example.ai_companion.weather;

import java.util.List;

/** Immutable aggregate over persisted natural-event history. */
public record WeatherEventStatistics(int events, int automaticEvents, int administratorEvents,
	long plannedDurationSeconds) {
	public WeatherEventStatistics {
		if (events < 0 || automaticEvents < 0 || administratorEvents < 0 || plannedDurationSeconds < 0)
			throw new IllegalArgumentException("统计值不能为负数");
		if (automaticEvents + administratorEvents != events)
			throw new IllegalArgumentException("自然与管理员事件数必须等于总事件数");
	}

	public static WeatherEventStatistics from(List<WeatherEventRecord> records) {
		int automatic = 0;
		long seconds = 0;
		for (WeatherEventRecord record : records) {
			if (record.automatic()) automatic++;
			seconds += record.plannedDurationSeconds();
		}
		return new WeatherEventStatistics(records.size(), automatic, records.size() - automatic, seconds);
	}
}
