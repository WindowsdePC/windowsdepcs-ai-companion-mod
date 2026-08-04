package com.example.ai_companion.photo;

import com.example.ai_companion.AiCompanionMod;
import com.example.ai_companion.ai.OpenAiCompatibleClient;
import com.example.ai_companion.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Captures server-side photo metadata, manages albums, and requests bounded AI reviews. */
public final class PhotographyManager implements AutoCloseable {
	private static final long CAPTURE_COOLDOWN_TICKS = 20;
	private static final String REVIEW_PROMPT = """
		你是 Minecraft 相册中的 AI 摄影评论员。你只能根据照片元数据、场景摘要和玩家说明进行评价。
		不要声称看见未提供的像素细节；资料不足时要明确说明。先用一句话描述可确认内容，再给一条构图或探索建议。
		回复使用玩家说明所用语言，最多四句话，不输出服务器命令。
		""";

	private final Supplier<ModConfig> config;
	private final PhotoAlbumStore albums;
	private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
	private final Set<UUID> reviewing = new HashSet<>();
	private final java.util.Map<UUID, Long> lastCaptureTick = new java.util.HashMap<>();

	public PhotographyManager(Supplier<ModConfig> config) {
		this(config, PhotoAlbumStore.load());
	}

	PhotographyManager(Supplier<ModConfig> config, PhotoAlbumStore albums) {
		this.config = config;
		this.albums = albums;
	}

	public PhotoEntry capture(ServerPlayer player) throws IOException {
		long nowTick = player.getServer().getTickCount();
		Long previous = lastCaptureTick.get(player.getUUID());
		if (previous != null && nowTick - previous < CAPTURE_COOLDOWN_TICKS) {
			throw new IllegalStateException("相机冷却中，请稍后再拍");
		}
		String weather = player.level().isThundering() ? "雷暴"
			: player.level().isRaining() ? "下雨" : "晴朗";
		long dayTime = Math.floorMod(player.level().getDayTime(), 24_000L);
		String scene = "天气=" + weather + "，世界时间=" + dayTime + "，脚下方块="
			+ player.level().getBlockState(player.blockPosition().below()).getBlock().getName().getString();
		PhotoEntry photo = albums.add(player.getUUID(),
			player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
			player.getYRot(), player.getXRot(), System.currentTimeMillis(), scene);
		lastCaptureTick.put(player.getUUID(), nowTick);
		return photo;
	}

	public List<PhotoEntry> photos(ServerPlayer player) {
		return albums.photos(player.getUUID());
	}

	public PhotoEntry require(ServerPlayer player, long id) {
		return albums.require(player.getUUID(), id);
	}

	public PhotoEntry caption(ServerPlayer player, long id, String caption) throws IOException {
		String normalized = caption == null ? "" : caption.strip();
		if (normalized.isBlank()) throw new IllegalArgumentException("照片说明不能为空");
		return albums.caption(player.getUUID(), id, normalized);
	}

	public boolean delete(ServerPlayer player, long id) throws IOException {
		return albums.delete(player.getUUID(), id);
	}

	public void review(MinecraftServer server, ServerPlayer player, long id) {
		PhotoEntry photo = require(player, id);
		synchronized (reviewing) {
			if (!reviewing.add(player.getUUID())) throw new IllegalStateException("已有照片正在等待 AI 评价");
		}
		String observation = String.format(Locale.ROOT,
			"照片编号=%d，维度=%s，坐标=(%.1f,%.1f,%.1f)，视角=(yaw %.1f,pitch %.1f)，场景=%s，玩家说明=%s",
			photo.id(), photo.dimension(), photo.x(), photo.y(), photo.z(), photo.yaw(), photo.pitch(),
			photo.sceneSummary(), photo.caption().isBlank() ? "无" : photo.caption());
		client.chat(config.get(), REVIEW_PROMPT, observation).whenComplete((answer, error) ->
			server.execute(() -> {
				synchronized (reviewing) {
					reviewing.remove(player.getUUID());
				}
				if (error != null) {
					AiCompanionMod.LOGGER.error("Photo review failed for {} photo {}",
						player.getScoreboardName(), id, error);
					player.sendSystemMessage(Component.literal("[AI相册] 评价失败：" + rootMessage(error)));
					return;
				}
				player.sendSystemMessage(Component.literal("[AI相册 · 照片 #" + id + "] " + answer));
			}));
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	@Override
	public void close() {
		synchronized (reviewing) {
			reviewing.clear();
		}
		lastCaptureTick.clear();
		albums.close();
	}
}
