package com.example.ai_companion.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AiDecisionTest {
	@Test void acceptsExecutableTaskActions() {
		assertEquals("attack", new AiDecision("ATTACK", "", 0, 0).sanitized().action());
		assertEquals("mine", new AiDecision("mine", "", 2, -1).sanitized().action());
		assertEquals("complete", new AiDecision("complete", "完成", 0, 0).sanitized().action());
	}

	@Test void rejectsUnknownActionsAndBoundsMovement() {
		AiDecision value = new AiDecision("teleport", "", 100, -100).sanitized();
		assertEquals("wait", value.action());
		assertEquals(8.0, value.dx());
		assertEquals(-8.0, value.dz());
	}
}
