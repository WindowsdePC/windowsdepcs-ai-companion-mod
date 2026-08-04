package com.example.ai_companion.orb;

/** One persistent reminder scheduled by an assistant orb. */
public record AssistantReminder(long id, long dueAtEpochMillis, String message) {
	public AssistantReminder {
		if (id < 1) throw new IllegalArgumentException("提醒编号无效");
		if (dueAtEpochMillis < 0) throw new IllegalArgumentException("提醒时间无效");
		if (message == null || message.isBlank() || message.length() > 200) {
			throw new IllegalArgumentException("提醒内容必须为 1-200 个字符");
		}
	}
}
