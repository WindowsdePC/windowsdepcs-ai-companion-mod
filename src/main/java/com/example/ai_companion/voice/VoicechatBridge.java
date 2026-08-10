package com.example.ai_companion.voice;

import java.util.function.BiConsumer;

/** Loader-neutral hand-off to the optional Simple Voice Chat plugin entrypoint. */
public final class VoicechatBridge {
	private static volatile BiConsumer<Object, short[]> speaker;

	private VoicechatBridge() { }

	public static void install(BiConsumer<Object, short[]> implementation) { speaker = implementation; }
	public static boolean available() { return speaker != null; }

	public static void speak(Object entity, short[] pcm48Khz) {
		BiConsumer<Object, short[]> current = speaker;
		if (current == null) throw new IllegalStateException("Simple Voice Chat API 尚未连接");
		current.accept(entity, pcm48Khz);
	}
}
