package com.example.ai_companion.news;

/** One bounded, server-observed event that can be included in a daily newspaper. */
public record NewsEvent(long id, long minecraftDay, long occurredAtEpochMillis,
		NewsCategory category, String message) {
	public NewsEvent {
		if (id < 1) throw new IllegalArgumentException("新闻事件编号必须为正数");
		if (minecraftDay < 0) throw new IllegalArgumentException("Minecraft 日期不能为负数");
		if (occurredAtEpochMillis < 0) throw new IllegalArgumentException("新闻事件时间无效");
		if (category == null) throw new IllegalArgumentException("新闻事件分类不能为空");
		message = bounded(message, 240, "新闻事件");
	}

	private static String bounded(String value, int maxLength, String label) {
		String normalized = value == null ? "" : value.strip();
		if (normalized.isBlank() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "必须为 1-" + maxLength + " 个字符");
		}
		return normalized;
	}
}
