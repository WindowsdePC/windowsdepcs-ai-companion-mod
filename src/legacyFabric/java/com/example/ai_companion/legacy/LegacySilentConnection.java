package com.example.ai_companion.legacy;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;

/** Unique in-memory connection used to register a visible 1.20.1 AI player. */
final class LegacySilentConnection extends Connection {
	LegacySilentConnection() {
		super(PacketFlow.SERVERBOUND);
		for (Class<?> type = Connection.class; type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!Channel.class.isAssignableFrom(field.getType())) continue;
				try {
					field.setAccessible(true);
					field.set(this, new EmbeddedChannel());
					return;
				} catch (ReflectiveOperationException error) {
					throw new IllegalStateException("无法初始化 1.20.1 AI 网络通道", error);
				}
			}
		}
		throw new IllegalStateException("找不到 1.20.1 玩家网络通道字段");
	}

	@Override public void setReadOnly() { }
	@Override public void handleDisconnection() { }
}
