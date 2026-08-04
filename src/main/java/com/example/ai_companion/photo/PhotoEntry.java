package com.example.ai_companion.photo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Immutable server-authoritative metadata for one album entry. */
public record PhotoEntry(long id, String dimension, double x, double y, double z, float yaw, float pitch,
		long capturedAtEpochMillis, String sceneSummary, String caption) {
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	public PhotoEntry {
		if (id < 1) throw new IllegalArgumentException("照片编号无效");
		if (dimension == null || dimension.isBlank() || dimension.length() > 128) {
			throw new IllegalArgumentException("维度标识无效");
		}
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
				|| !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
			throw new IllegalArgumentException("照片坐标或视角无效");
		}
		if (capturedAtEpochMillis < 0) throw new IllegalArgumentException("拍摄时间无效");
		sceneSummary = normalize(sceneSummary, 300, "场景摘要");
		caption = caption == null ? "" : caption.strip();
		if (caption.length() > 200) throw new IllegalArgumentException("照片说明不能超过 200 个字符");
	}

	public PhotoEntry withCaption(String value) {
		return new PhotoEntry(id, dimension, x, y, z, yaw, pitch, capturedAtEpochMillis, sceneSummary, value);
	}

	public String displayText() {
		String suffix = caption.isBlank() ? "" : " · “" + caption + "”";
		return String.format(Locale.ROOT, "#%d · %s · %s · X %.1f, Y %.1f, Z %.1f%s",
			id, TIME_FORMAT.format(Instant.ofEpochMilli(capturedAtEpochMillis)), dimension, x, y, z, suffix);
	}

	private static String normalize(String value, int maxLength, String label) {
		String normalized = value == null ? "" : value.strip();
		if (normalized.isBlank() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "必须为 1-" + maxLength + " 个字符");
		}
		return normalized;
	}
}
