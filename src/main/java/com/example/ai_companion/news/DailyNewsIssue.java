package com.example.ai_companion.news;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One persisted Minecraft Daily issue, with an optional AI-written edition. */
public record DailyNewsIssue(long id, long minecraftDay, long generatedAtEpochMillis,
		String title, String body, String aiEdition) {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	public DailyNewsIssue {
		if (id < 1) throw new IllegalArgumentException("日报编号必须为正数");
		if (minecraftDay < 0) throw new IllegalArgumentException("Minecraft 日期不能为负数");
		if (generatedAtEpochMillis < 0) throw new IllegalArgumentException("日报生成时间无效");
		title = bounded(title, 120, "日报标题", false);
		body = bounded(body, 8_000, "日报正文", false);
		aiEdition = bounded(aiEdition, 4_000, "AI版日报", true);
	}

	public DailyNewsIssue withAiEdition(String text) {
		return new DailyNewsIssue(id, minecraftDay, generatedAtEpochMillis, title, body, text);
	}

	public String summaryText() {
		return "#" + id + " · Minecraft日报 " + LocalDate.ofEpochDay(minecraftDay) + " · " + title
			+ (aiEdition.isBlank() ? "" : " · 已生成AI版");
	}

	public String displayText() {
		String edition = aiEdition.isBlank() ? body : aiEdition;
		return summaryText() + "\n生成时间：" + DATE.format(Instant.ofEpochMilli(generatedAtEpochMillis))
			+ "\n" + edition;
	}

	private static String bounded(String value, int maxLength, String label, boolean blankAllowed) {
		String normalized = value == null ? "" : value.strip();
		if ((!blankAllowed && normalized.isBlank()) || normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "长度无效，最多 " + maxLength + " 个字符");
		}
		return normalized;
	}
}
