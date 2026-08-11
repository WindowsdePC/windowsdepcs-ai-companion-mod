package com.example.ai_companion.agent;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AgentPositionTest {
	@Test
	void displayTextUsesStableCoordinatesAndDimension() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			AgentPosition position = new AgentPosition("Builder_AI", "minecraft:overworld",
				12.25, 64.0, -8.75);
			assertEquals("Builder_AI [survival] · minecraft:overworld · X 12.3, Y 64.0, Z -8.8",
				position.displayText());
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void snapshotRejectsMissingIdentityFields() {
		assertThrows(IllegalArgumentException.class,
			() -> new AgentPosition("", "minecraft:overworld", 0, 0, 0));
		assertThrows(IllegalArgumentException.class,
			() -> new AgentPosition("AI_1", " ", 0, 0, 0));
		assertThrows(IllegalArgumentException.class,
			() -> new AgentPosition("AI_1", "minecraft:overworld", Double.NaN, 0, 0));
	}

	@Test
	void snapshotCarriesModeAndBoundsPopupMessage() {
		AgentPosition position = new AgentPosition("AI_1", "minecraft:overworld", 0, 64, 0,
			AgentMode.HUNTER, "x".repeat(600));
		assertEquals(AgentMode.HUNTER, position.mode());
		assertEquals(512, position.lastMessage().length());
	}

	@Test
	void detailedSnapshotCarriesEntityAndPlannerState() {
		AgentPosition position = new AgentPosition("Maid_01", "12345678-1234-1234-1234-123456789012",
			"minecraft:overworld", 1, 65, 2, 17.5F, 24.0F, "survival", AgentMode.TEAMMATE,
			"Owner", "maid", true, "跟随主人并收集木头", "正在规划");
		assertEquals("survival", position.gameMode());
		assertEquals("maid", position.promptId());
		assertEquals("跟随主人并收集木头", position.activeTask());
		assertEquals(17.5F, position.health());
	}
}
