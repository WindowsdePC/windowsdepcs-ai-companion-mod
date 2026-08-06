package com.example.ai_companion.society;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent social/economic state for one registered AI identity. */
public record SocietyResident(String agentName, String homeDimension, double homeX, double homeY, double homeZ,
		SocietyJob job, int balance, int completedWorkCycles, long lastWorkEpochMillis,
		Map<String, Integer> relationships) {
	public SocietyResident {
		if (agentName == null || !agentName.matches("[A-Za-z0-9_]{3,16}")) {
			throw new IllegalArgumentException("社会成员必须引用有效的 AI 名称");
		}
		homeDimension = homeDimension == null ? "" : homeDimension.strip();
		if (!Double.isFinite(homeX) || !Double.isFinite(homeY) || !Double.isFinite(homeZ)) {
			homeDimension = ""; homeX = 0; homeY = 0; homeZ = 0;
		}
		job = job == null ? SocietyJob.UNEMPLOYED : job;
		balance = Math.max(0, Math.min(1_000_000, balance));
		completedWorkCycles = Math.max(0, completedWorkCycles);
		lastWorkEpochMillis = Math.max(0L, lastWorkEpochMillis);
		Map<String, Integer> normalized = new LinkedHashMap<>();
		if (relationships != null) relationships.entrySet().stream().limit(127).forEach(entry -> {
			if (entry.getKey() != null && entry.getKey().matches("[A-Za-z0-9_]{3,16}")) {
				normalized.put(entry.getKey(), SocietyRules.relationship(0, entry.getValue() == null ? 0 : entry.getValue()));
			}
		});
		relationships = Map.copyOf(normalized);
	}

	public static SocietyResident enroll(String agentName) {
		return new SocietyResident(agentName, "", 0, 0, 0, SocietyJob.UNEMPLOYED,
			20, 0, 0L, Map.of());
	}

	public boolean hasHome() { return !homeDimension.isBlank(); }

	public SocietyResident withHome(String dimension, double x, double y, double z) {
		return new SocietyResident(agentName, dimension, x, y, z, job, balance,
			completedWorkCycles, lastWorkEpochMillis, relationships);
	}

	public SocietyResident withJob(SocietyJob selected) {
		return new SocietyResident(agentName, homeDimension, homeX, homeY, homeZ, selected,
			balance, completedWorkCycles, lastWorkEpochMillis, relationships);
	}

	public SocietyResident work(long now) {
		if (job == SocietyJob.UNEMPLOYED) throw new IllegalStateException("请先为 AI 分配职业");
		if (now - lastWorkEpochMillis < 60_000L) throw new IllegalStateException("同一名 AI 每 60 秒只能手动工作一次");
		int income = SocietyRules.workIncome(job, completedWorkCycles);
		return new SocietyResident(agentName, homeDimension, homeX, homeY, homeZ, job,
			Math.min(1_000_000, balance + income), completedWorkCycles + 1, now, relationships);
	}

	public SocietyResident dayCycle() {
		return new SocietyResident(agentName, homeDimension, homeX, homeY, homeZ, job,
			SocietyRules.dailyBalance(balance, job, hasHome(), completedWorkCycles),
			completedWorkCycles, lastWorkEpochMillis, relationships);
	}

	public SocietyResident relate(String other, int change) {
		Map<String, Integer> updated = new LinkedHashMap<>(relationships);
		updated.put(other, SocietyRules.relationship(updated.getOrDefault(other, 0), change));
		return new SocietyResident(agentName, homeDimension, homeX, homeY, homeZ, job,
			balance, completedWorkCycles, lastWorkEpochMillis, updated);
	}

	public int relationshipWith(String other) { return relationships.getOrDefault(other, 0); }
	public int wellbeing() { return SocietyRules.wellbeing(hasHome(), job, balance, relationships); }

	public String displayText() {
		String home = hasHome() ? "%s (%.0f, %.0f, %.0f)".formatted(homeDimension, homeX, homeY, homeZ) : "未设置";
		return "%s · 家=%s · 职业=%s · 余额=%d · 工作=%d · 幸福=%d"
			.formatted(agentName, home, job.name().toLowerCase(), balance, completedWorkCycles, wellbeing());
	}
}
