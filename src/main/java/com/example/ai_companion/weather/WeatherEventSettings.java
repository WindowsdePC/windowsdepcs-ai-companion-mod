package com.example.ai_companion.weather;

/** Bounded server policy for automatic natural events. */
public record WeatherEventSettings(boolean automaticEnabled, int checkIntervalSeconds,
	int chanceDenominator, int minDurationMinutes, int maxDurationMinutes) {
	public WeatherEventSettings {
		if (checkIntervalSeconds < 30 || checkIntervalSeconds > 3600)
			throw new IllegalArgumentException("检查间隔必须为 30～3600 秒");
		if (chanceDenominator < 1 || chanceDenominator > 10000)
			throw new IllegalArgumentException("概率分母必须为 1～10000");
		if (minDurationMinutes < 1 || maxDurationMinutes > 30 || minDurationMinutes > maxDurationMinutes)
			throw new IllegalArgumentException("自动时长必须满足 1 ≤ 最短 ≤ 最长 ≤ 30 分钟");
	}

	public static WeatherEventSettings defaults() { return new WeatherEventSettings(true, 60, 240, 5, 10); }
	public WeatherEventSettings withEnabled(boolean value) { return new WeatherEventSettings(value, checkIntervalSeconds, chanceDenominator, minDurationMinutes, maxDurationMinutes); }
	public WeatherEventSettings withInterval(int value) { return new WeatherEventSettings(automaticEnabled, value, chanceDenominator, minDurationMinutes, maxDurationMinutes); }
	public WeatherEventSettings withChance(int value) { return new WeatherEventSettings(automaticEnabled, checkIntervalSeconds, value, minDurationMinutes, maxDurationMinutes); }
	public WeatherEventSettings withDuration(int minimum, int maximum) { return new WeatherEventSettings(automaticEnabled, checkIntervalSeconds, chanceDenominator, minimum, maximum); }
}
