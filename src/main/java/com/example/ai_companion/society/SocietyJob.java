package com.example.ai_companion.society;

import java.util.Locale;

/** Stable jobs and wages used by the AI society economy. */
public enum SocietyJob {
	UNEMPLOYED("待业", 0), FARMER("农夫", 12), BUILDER("建筑师", 16), MINER("矿工", 18),
	MERCHANT("商人", 20), GUARD("守卫", 15), ARTIST("艺术家", 14);

	private final String displayName;
	private final int wage;

	SocietyJob(String displayName, int wage) {
		this.displayName = displayName;
		this.wage = wage;
	}

	public String displayName() { return displayName; }
	public int wage() { return wage; }

	public static SocietyJob parse(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "farmer", "农夫" -> FARMER;
			case "builder", "建筑师" -> BUILDER;
			case "miner", "矿工" -> MINER;
			case "merchant", "商人" -> MERCHANT;
			case "guard", "守卫" -> GUARD;
			case "artist", "艺术家" -> ARTIST;
			case "unemployed", "待业" -> UNEMPLOYED;
			default -> throw new IllegalArgumentException("职业必须是 farmer、builder、miner、merchant、guard 或 artist");
		};
	}
}
