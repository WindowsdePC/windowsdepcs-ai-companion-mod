package com.example.ai_companion.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PromptTemplatesTest {
	@Test
	void survivalIsAFirstClassDefaultPreset() {
		assertEquals(PromptTemplates.SURVIVAL_PRESET, PromptTemplates.defaults().get("survival"));
		assertTrue(PromptTemplates.SURVIVAL_PRESET.contains("模式：生存"));
	}
}
