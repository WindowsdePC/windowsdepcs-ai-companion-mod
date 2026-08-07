package com.example.ai_companion.weather;

/** One bounded administrator-created event schedule. */
public record ScheduledWeatherEvent(int id, WeatherEventType type, long scheduledAtEpochMillis,
	int durationMinutes) implements Comparable<ScheduledWeatherEvent> {
	public ScheduledWeatherEvent {
		if (id < 1) throw new IllegalArgumentException("日程编号必须为正数");
		if (type == null) throw new IllegalArgumentException("事件类型不能为空");
		if (scheduledAtEpochMillis < 1) throw new IllegalArgumentException("执行时间无效");
		if (durationMinutes < 1 || durationMinutes > 30) throw new IllegalArgumentException("时长必须为 1～30 分钟");
	}

	public boolean due(long nowMillis) { return nowMillis >= scheduledAtEpochMillis; }
	public boolean eligible(boolean night) { return night || !type.nightOnly(); }

	@Override public int compareTo(ScheduledWeatherEvent other) {
		int time = Long.compare(scheduledAtEpochMillis, other.scheduledAtEpochMillis);
		return time != 0 ? time : Integer.compare(id, other.id);
	}
}
