package com.example.ai_companion.weather;

/** Persisted event state; remaining ticks are clamped before use. */
public record ActiveWeatherEvent(WeatherEventType type, long remainingTicks, long totalTicks, boolean automatic) {
	public ActiveWeatherEvent {
		if (type == null) throw new IllegalArgumentException("事件类型不能为空");
		if (totalTicks < 20 || totalTicks > 20L * 60L * 30L) throw new IllegalArgumentException("事件时长必须为 1 秒至 30 分钟");
		remainingTicks = Math.max(0, Math.min(remainingTicks, totalTicks));
	}

	public ActiveWeatherEvent nextTick() { return new ActiveWeatherEvent(type, Math.max(0, remainingTicks - 1), totalTicks, automatic); }
	public boolean expired() { return remainingTicks <= 0; }
	public int remainingSeconds() { return (int) Math.ceil(remainingTicks / 20.0); }
}
