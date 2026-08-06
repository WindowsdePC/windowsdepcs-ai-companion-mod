package com.example.ai_companion.society;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persistent home, job, economy and social state for one registered AI. */
public record SocietyProfile(String agentName, String homeDimension, double homeX, double homeY, double homeZ,
		SocietyJob job, long balance, int energy, int reputation, Map<String, Integer> relationships,
		long lastWorkMillis) {
	public SocietyProfile {
		if (agentName == null || agentName.isBlank() || agentName.length() > 16) {
			throw new IllegalArgumentException("AI 名称无效");
		}
		homeDimension = homeDimension == null ? "" : homeDimension.strip();
		if (homeDimension.length() > 128 || !Double.isFinite(homeX) || !Double.isFinite(homeY)
				|| !Double.isFinite(homeZ)) throw new IllegalArgumentException("住宅坐标无效");
		job = job == null ? SocietyJob.UNEMPLOYED : job;
		if (balance < 0 || energy < 0 || energy > 100 || reputation < 0 || lastWorkMillis < 0) {
			throw new IllegalArgumentException("社会档案数值无效");
		}
		Map<String, Integer> safe = new LinkedHashMap<>();
		if (relationships != null) relationships.forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				safe.put(name.toLowerCase(Locale.ROOT), Math.clamp(value, -100, 100));
			}
		});
		relationships = Map.copyOf(safe);
	}

	public static SocietyProfile enroll(String agentName) {
		return new SocietyProfile(agentName, "", 0, 0, 0, SocietyJob.UNEMPLOYED,
			0, 100, 0, Map.of(), 0);
	}

	public boolean hasHome() { return !homeDimension.isBlank(); }

	public SocietyProfile withHome(String dimension, double x, double y, double z) {
		return new SocietyProfile(agentName, dimension, x, y, z, job, balance, energy,
			reputation, relationships, lastWorkMillis);
	}

	public SocietyProfile withJob(SocietyJob selected) {
		return new SocietyProfile(agentName, homeDimension, homeX, homeY, homeZ, selected,
			balance, energy, reputation, relationships, lastWorkMillis);
	}

	public SocietyProfile work(long nowMillis) {
		if (!hasHome()) throw new IllegalStateException("请先为 AI 设置住宅");
		if (job == SocietyJob.UNEMPLOYED) throw new IllegalStateException("请先为 AI 分配工作");
		if (energy < 15) throw new IllegalStateException("AI 精力不足，需要休息");
		if (nowMillis < lastWorkMillis + 60_000L) throw new IllegalStateException("工作冷却尚未结束");
		return new SocietyProfile(agentName, homeDimension, homeX, homeY, homeZ, job,
			balance + job.wage(), energy - 15, reputation + 1, relationships, nowMillis);
	}

	public SocietyProfile rest() {
		return new SocietyProfile(agentName, homeDimension, homeX, homeY, homeZ, job,
			balance, 100, reputation, relationships, lastWorkMillis);
	}

	public SocietyProfile relate(String other, int delta) {
		if (other.equalsIgnoreCase(agentName)) throw new IllegalArgumentException("AI 不能与自己社交");
		Map<String, Integer> updated = new LinkedHashMap<>(relationships);
		String key = other.toLowerCase(Locale.ROOT);
		updated.put(key, Math.clamp(updated.getOrDefault(key, 0) + delta, -100, 100));
		return new SocietyProfile(agentName, homeDimension, homeX, homeY, homeZ, job,
			balance, Math.max(0, energy - 3), reputation + 1, updated, lastWorkMillis);
	}

	public SocietyProfile transfer(long delta) {
		if (balance + delta < 0) throw new IllegalStateException("AI 余额不足");
		return new SocietyProfile(agentName, homeDimension, homeX, homeY, homeZ, job,
			balance + delta, energy, reputation, relationships, lastWorkMillis);
	}

	public int relationWith(String other) {
		return relationships.getOrDefault(other.toLowerCase(Locale.ROOT), 0);
	}
}
