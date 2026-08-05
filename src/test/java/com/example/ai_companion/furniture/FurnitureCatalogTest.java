package com.example.ai_companion.furniture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FurnitureCatalogTest {
	@Test
	void catalogHasFourUniqueBoundedEntries() {
		assertEquals(4, FurnitureCatalog.ALL.size());
		assertEquals(4, FurnitureCatalog.ALL.stream().map(FurnitureCatalog.Definition::id).distinct().count());
		assertTrue(FurnitureCatalog.SOFA.seat());
		assertFalse(FurnitureCatalog.TELEVISION.seat());
		assertEquals(12, FurnitureCatalog.TABLE_LAMP.lightLevel());
	}

	@Test
	void invalidDefinitionsAreRejectedOrClamped() {
		assertThrows(IllegalArgumentException.class,
			() -> new FurnitureCatalog.Definition("Bad ID", "坏", false, 0));
		assertEquals(15, new FurnitureCatalog.Definition("lamp", "灯", false, 99).lightLevel());
	}
}
