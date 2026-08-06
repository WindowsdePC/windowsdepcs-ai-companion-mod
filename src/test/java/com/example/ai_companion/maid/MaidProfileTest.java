package com.example.ai_companion.maid;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MaidProfileTest {
	@Test void defaultSkinNamesMatchUploadedFilenames() {
		assertEquals(7, MaidSkins.DEFAULTS.size());
		assertTrue(MaidSkins.DEFAULTS.contains("1000030746"));
		assertTrue(MaidSkins.DEFAULTS.stream().noneMatch(name -> name.contains("(") || name.endsWith(".png")));
	}

	@Test void profilePreservesOwnerAndMood() {
		UUID owner = UUID.randomUUID();
		MaidProfile profile = new MaidProfile("Maid_01", owner, "Steve", "1000030746", "",
			MaidMood.CALM, false).withMood(MaidMood.HAPPY);
		assertEquals(owner, profile.ownerUuid());
		assertEquals(MaidMood.HAPPY, profile.mood());
		assertFalse(profile.stored());
	}

	@Test void unsafeNamesAndTextureKeysAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new MaidProfile("x", UUID.randomUUID(),
			"Steve", "1000030746", "", MaidMood.CALM, false));
		assertThrows(IllegalArgumentException.class, () -> MaidSkins.validate("../../secret key"));
	}

	@Test void ownershipAndStoredStateChangeWithoutLosingAppearance() {
		MaidProfile original = new MaidProfile("Maid_02", UUID.randomUUID(), "Alex",
			"1000030745", "custom/cape", MaidMood.HAPPY, false);
		UUID nextOwner = UUID.randomUUID();
		MaidProfile changed = original.withStored(true).withOwner(nextOwner, "Steve");
		assertTrue(changed.stored());
		assertEquals(nextOwner, changed.ownerUuid());
		assertEquals("1000030745", changed.skinKey());
		assertEquals("custom/cape", changed.capeKey());
	}
}
