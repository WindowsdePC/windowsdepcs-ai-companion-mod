package com.example.ai_companion.society;

import java.util.Locale;

/** First stable set of bounded jobs in the AI society simulation. */
public enum SocietyJob {
	UNEMPLOYED(0), FARMER(8), BUILDER(10), EXPLORER(9), GUARD(11), TRADER(12);

	private final int baseWage;
	SocietyJob(int baseWage) { this.baseWage = baseWage; }
	public int baseWage() { return baseWage; }

	public static SocietyJob parse(String value) {
		try { return valueOf(value.strip().toUpperCase(Locale.ROOT)); }
		catch (RuntimeException error) {
			throw new IllegalArgumentException("职业只能是 unemployed、farmer、builder、explorer、guard 或 trader");
		}
	}
}
