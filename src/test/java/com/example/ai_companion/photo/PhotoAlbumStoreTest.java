package com.example.ai_companion.photo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhotoAlbumStoreTest {
	@TempDir
	Path directory;

	@Test
	void albumSurvivesReloadAndKeepsMonotonicIds() throws Exception {
		Path file = directory.resolve("albums.json");
		UUID player = UUID.randomUUID();
		PhotoAlbumStore first = PhotoAlbumStore.load(file);
		PhotoEntry photo = first.add(player, "minecraft:overworld", 12.5, 64, -4.5,
			90, -10, 100, "天气=晴朗，脚下方块=草方块");
		first.caption(player, photo.id(), "村庄入口");

		PhotoAlbumStore second = PhotoAlbumStore.load(file);
		assertEquals("村庄入口", second.require(player, photo.id()).caption());
		PhotoEntry next = second.add(player, "minecraft:the_nether", 1, 70, 2,
			0, 0, 200, "天气=晴朗，脚下方块=下界岩");
		assertEquals(photo.id() + 1, next.id());
		assertTrue(second.delete(player, photo.id()));
		assertFalse(second.delete(player, photo.id()));
	}

	@Test
	void photoMetadataAndCaptionAreBounded() {
		assertThrows(IllegalArgumentException.class,
			() -> new PhotoEntry(1, "minecraft:overworld", Double.NaN, 0, 0,
				0, 0, 0, "scene", ""));
		assertThrows(IllegalArgumentException.class,
			() -> new PhotoEntry(1, "minecraft:overworld", 0, 0, 0,
				0, 0, 0, "scene", "x".repeat(201)));
	}
}
