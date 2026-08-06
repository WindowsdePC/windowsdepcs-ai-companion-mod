package com.example.ai_companion.society;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SocietyProfileTest {
	@Test
	void workRequiresHomeJobEnergyAndCooldown() {
		SocietyProfile profile = SocietyProfile.enroll("BuilderAI")
			.withHome("minecraft:overworld", 1, 64, 2).withJob(SocietyJob.BUILDER);
		SocietyProfile paid = profile.work(60_000L);
		assertEquals(16, paid.balance());
		assertEquals(85, paid.energy());
		assertThrows(IllegalStateException.class, () -> paid.work(119_999L));
	}

	@Test
	void relationshipsAndTransfersAreBounded() {
		SocietyProfile first = SocietyProfile.enroll("First").relate("Second", 150);
		assertEquals(100, first.relationWith("Second"));
		assertThrows(IllegalStateException.class, () -> first.transfer(-1));
		assertEquals(20, first.transfer(20).balance());
	}
}
