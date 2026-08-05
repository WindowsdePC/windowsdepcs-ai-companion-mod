package com.example.ai_companion.travel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One immutable, server-authoritative adventure-compendium discovery. */
public record TravelLogEntry(long id, TravelLogCategory category, String discoveryKey,
		String name, String dimension, double x, double y, double z,
		long discoveredAtEpochMillis, long photoId) {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	public TravelLogEntry {
		if (id < 1) throw new IllegalArgumentException("旅行日志编号必须为正数");
		if (category == null) throw new IllegalArgumentException("旅行日志分类不能为空");
		discoveryKey = bounded(discoveryKey, 240, "发现键");
		name = bounded(name, 160, "地点名称");
		dimension = bounded(dimension, 160, "维度");
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("旅行日志坐标必须是有限值");
		}
		if (discoveredAtEpochMillis < 0) throw new IllegalArgumentException("发现时间无效");
		if (photoId < 0) throw new IllegalArgumentException("照片编号不能为负数");
	}

	public TravelLogEntry withPhoto(long linkedPhotoId) {
		return new TravelLogEntry(id, category, discoveryKey, name, dimension, x, y, z,
			discoveredAtEpochMillis, linkedPhotoId);
	}

	public String displayText() {
		String photo = photoId == 0 ? "" : " · 照片 #" + photoId;
		return String.format(Locale.ROOT, "#%d [%s] %s · %s · X %.1f, Y %.1f, Z %.1f%s",
			id, category.displayName(), name, dimension, x, y, z, photo);
	}

	public String detailText() {
		return displayText() + "\n发现时间：" + DATE.format(Instant.ofEpochMilli(discoveredAtEpochMillis));
	}

	private static String bounded(String value, int maxLength, String label) {
		String normalized = value == null ? "" : value.strip();
		if (normalized.isBlank() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "必须为 1-" + maxLength + " 个字符");
		}
		return normalized;
	}
}
