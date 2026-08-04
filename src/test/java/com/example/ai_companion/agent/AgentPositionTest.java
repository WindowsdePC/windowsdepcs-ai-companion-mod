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
			assertEquals("Builder_AI · minecraft:overworld · X 12.3, Y 64.0, Z -8.8",
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
}
