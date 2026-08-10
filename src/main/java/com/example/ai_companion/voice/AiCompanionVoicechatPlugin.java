package com.example.ai_companion.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.UUID;

/** Optional Simple Voice Chat API plugin that attaches synthesized audio to the AI entity. */
public final class AiCompanionVoicechatPlugin implements VoicechatPlugin {
	@Override public String getPluginId() { return "ai_companion"; }

	@Override public void initialize(VoicechatApi api) {
		if (!(api instanceof VoicechatServerApi serverApi)) return;
		VoicechatBridge.install((entity, pcm) -> {
			var voiceEntity = serverApi.fromEntity(entity);
			var channel = serverApi.createEntityAudioChannel(UUID.randomUUID(), voiceEntity);
			if (channel == null) throw new IllegalStateException("无法创建 AI 实体语音频道");
			var encoder = serverApi.createEncoder();
			var player = serverApi.createAudioPlayer(channel, encoder, pcm);
			player.setOnStopped(() -> {
				try { channel.flush(); }
				finally { encoder.close(); }
			});
			player.startPlaying();
		});
	}
}
