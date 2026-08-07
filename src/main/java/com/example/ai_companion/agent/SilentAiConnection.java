package com.example.ai_companion.agent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;

/** A unique, connected-looking network endpoint for a server-controlled player. */
final class SilentAiConnection extends Connection {
	SilentAiConnection() {
		super(PacketFlow.SERVERBOUND);
		installEmbeddedChannel();
	}

	private void installEmbeddedChannel() {
		for (Class<?> type = Connection.class; type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!Channel.class.isAssignableFrom(field.getType())) continue;
				try {
					field.setAccessible(true);
					field.set(this, new EmbeddedChannel());
					return;
				} catch (ReflectiveOperationException error) {
					throw new IllegalStateException("无法初始化 AI 玩家网络通道", error);
				}
			}
		}
		throw new IllegalStateException("当前 Minecraft 版本中找不到玩家网络通道字段");
	}

	@Override
	public void setReadOnly() {
	}

	@Override
	public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
	}

	@Override
	public void handleDisconnection() {
	}

	@Override
	public void setListenerForServerboundHandshake(PacketListener listener) {
	}

	@Override
	public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
	}
}
