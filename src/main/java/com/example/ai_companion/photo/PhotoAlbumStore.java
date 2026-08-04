package com.example.ai_companion.photo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ai_companion.AiCompanionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded JSON persistence for player photo albums. */
public final class PhotoAlbumStore implements AutoCloseable {
	public static final int MAX_PHOTOS_PER_PLAYER = 256;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class PlayerAlbum {
		List<PhotoEntry> photos = new ArrayList<>();
		long nextPhotoId = 1;
	}

	private static final class FileData {
		Map<String, PlayerAlbum> albums = new LinkedHashMap<>();
	}

	private final Path path;
	private final Map<String, PlayerAlbum> albums;

	public static PhotoAlbumStore load() {
		Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("windowsdepcs-ai-companion-albums.json");
		try {
			return load(path);
		} catch (Exception error) {
			AiCompanionMod.LOGGER.error("Cannot read {}; starting with empty photo albums", path, error);
			return new PhotoAlbumStore(path, new LinkedHashMap<>());
		}
	}

	static PhotoAlbumStore load(Path path) throws IOException {
		if (Files.notExists(path)) return new PhotoAlbumStore(path, new LinkedHashMap<>());
		FileData loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), FileData.class);
		Map<String, PlayerAlbum> data = loaded == null || loaded.albums == null
			? new LinkedHashMap<>() : loaded.albums;
		data.values().forEach(PhotoAlbumStore::normalize);
		return new PhotoAlbumStore(path, data);
	}

	private PhotoAlbumStore(Path path, Map<String, PlayerAlbum> albums) {
		this.path = path;
		this.albums = albums;
	}

	public synchronized PhotoEntry add(UUID playerId, String dimension, double x, double y, double z,
			float yaw, float pitch, long capturedAtEpochMillis, String sceneSummary) throws IOException {
		PlayerAlbum album = album(playerId);
		if (album.photos.size() >= MAX_PHOTOS_PER_PLAYER) {
			throw new IllegalStateException("相册已满；每位玩家最多保存 " + MAX_PHOTOS_PER_PLAYER + " 张照片");
		}
		PhotoEntry photo = new PhotoEntry(album.nextPhotoId++, dimension, x, y, z, yaw, pitch,
			capturedAtEpochMillis, sceneSummary, "");
		album.photos.add(photo);
		save();
		return photo;
	}

	public synchronized List<PhotoEntry> photos(UUID playerId) {
		return album(playerId).photos.stream()
			.sorted(Comparator.comparingLong(PhotoEntry::id).reversed()).toList();
	}

	public synchronized PhotoEntry require(UUID playerId, long id) {
		return album(playerId).photos.stream().filter(photo -> photo.id() == id).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("找不到照片 #" + id));
	}

	public synchronized PhotoEntry caption(UUID playerId, long id, String caption) throws IOException {
		PlayerAlbum album = album(playerId);
		for (int index = 0; index < album.photos.size(); index++) {
			PhotoEntry photo = album.photos.get(index);
			if (photo.id() != id) continue;
			PhotoEntry updated = photo.withCaption(caption);
			album.photos.set(index, updated);
			save();
			return updated;
		}
		throw new IllegalArgumentException("找不到照片 #" + id);
	}

	public synchronized boolean delete(UUID playerId, long id) throws IOException {
		boolean removed = album(playerId).photos.removeIf(photo -> photo.id() == id);
		if (removed) save();
		return removed;
	}

	private PlayerAlbum album(UUID playerId) {
		return albums.computeIfAbsent(playerId.toString(), ignored -> new PlayerAlbum());
	}

	private static void normalize(PlayerAlbum album) {
		if (album.photos == null) album.photos = new ArrayList<>();
		album.photos = new ArrayList<>(album.photos.stream().limit(MAX_PHOTOS_PER_PLAYER).toList());
		long next = album.photos.stream().mapToLong(PhotoEntry::id).max().orElse(0) + 1;
		album.nextPhotoId = Math.max(Math.max(1, album.nextPhotoId), next);
	}

	private void save() throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		FileData root = new FileData();
		root.albums = albums;
		Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException unsupportedAtomicMove) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Override
	public synchronized void close() {
		try {
			save();
		} catch (IOException error) {
			AiCompanionMod.LOGGER.error("Cannot save photo albums to {}", path, error);
		}
	}
}
