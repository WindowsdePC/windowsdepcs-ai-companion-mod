package com.example.ai_companion.client.minigame;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Personality-weighted AI opponent for the Rock-Paper-Scissors minigame. */
public final class RockPaperScissorsGame {
	public enum Choice {
		ROCK("石头", "■"), SCISSORS("剪刀", "✂"), PAPER("布", "▤");

		private final String displayName;
		private final String symbol;

		Choice(String displayName, String symbol) {
			this.displayName = displayName;
			this.symbol = symbol;
		}

		public String displayName() {
			return displayName;
		}

		public String symbol() {
			return symbol;
		}
	}

	public enum Personality {
		CALM("冷静", "三种选择概率相同，不追逐短期输赢"),
		COMPETITIVE("好胜", "观察玩家历史选择并更积极地尝试克制"),
		MISCHIEVOUS("淘气", "偏爱针对玩家上一手，也会突然改变主意");

		private final String displayName;
		private final String description;

		Personality(String displayName, String description) {
			this.displayName = displayName;
			this.description = description;
		}

		public String displayName() {
			return displayName;
		}

		public String description() {
			return description;
		}
	}

	public enum Outcome { PLAYER_WIN, AI_WIN, DRAW }

	public record Round(Choice playerChoice, Choice aiChoice, Outcome outcome, String aiMessage) { }

	private final Random random;
	private final List<Choice> playerHistory = new ArrayList<>();
	private final List<Round> rounds = new ArrayList<>();
	private Personality personality = Personality.CALM;

	public RockPaperScissorsGame() {
		this(new Random());
	}

	/** Allows deterministic seeds in logic verification without changing normal gameplay. */
	public RockPaperScissorsGame(Random random) {
		this.random = Objects.requireNonNull(random, "random");
	}

	public Round play(Choice playerChoice) {
		Objects.requireNonNull(playerChoice, "playerChoice");
		Choice aiChoice = chooseForPersonality();
		Outcome outcome = determineWinner(playerChoice, aiChoice);
		String message = switch (outcome) {
			case AI_WIN -> "看来今天我的运气不错。";
			case PLAYER_WIN -> "下一次我一定赢回来。";
			case DRAW -> "我们想到一块去了，再来一局！";
		};
		Round round = new Round(playerChoice, aiChoice, outcome, message);
		playerHistory.add(playerChoice);
		rounds.add(round);
		return round;
	}

	private Choice chooseForPersonality() {
		return switch (personality) {
			case CALM -> randomChoice();
			case COMPETITIVE -> {
				Choice prediction = mostFrequentPlayerChoice();
				yield prediction != null && random.nextInt(100) < 65 ? counterTo(prediction) : randomChoice();
			}
			case MISCHIEVOUS -> {
				Choice previous = playerHistory.isEmpty() ? null : playerHistory.getLast();
				int roll = random.nextInt(100);
				if (previous == null || roll >= 75) yield randomChoice();
				yield roll < 50 ? counterTo(previous) : previous;
			}
		};
	}

	private Choice mostFrequentPlayerChoice() {
		if (playerHistory.isEmpty()) return null;
		Map<Choice, Integer> counts = new EnumMap<>(Choice.class);
		for (Choice choice : playerHistory) counts.merge(choice, 1, Integer::sum);
		Choice best = null;
		int bestCount = -1;
		for (Choice choice : Choice.values()) {
			int count = counts.getOrDefault(choice, 0);
			if (count > bestCount) {
				best = choice;
				bestCount = count;
			}
		}
		return best;
	}

	private Choice randomChoice() {
		Choice[] values = Choice.values();
		return values[random.nextInt(values.length)];
	}

	private static Choice counterTo(Choice choice) {
		return switch (choice) {
			case ROCK -> Choice.PAPER;
			case SCISSORS -> Choice.ROCK;
			case PAPER -> Choice.SCISSORS;
		};
	}

	public static Outcome determineWinner(Choice player, Choice ai) {
		if (player == ai) return Outcome.DRAW;
		boolean playerWins = player == Choice.ROCK && ai == Choice.SCISSORS
			|| player == Choice.SCISSORS && ai == Choice.PAPER
			|| player == Choice.PAPER && ai == Choice.ROCK;
		return playerWins ? Outcome.PLAYER_WIN : Outcome.AI_WIN;
	}

	public Personality cyclePersonality() {
		Personality[] values = Personality.values();
		personality = values[(personality.ordinal() + 1) % values.length];
		return personality;
	}

	public Personality personality() {
		return personality;
	}

	public List<Round> rounds() {
		return List.copyOf(rounds);
	}
}
