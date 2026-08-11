package com.example.ai_companion.music;

import com.example.ai_companion.agent.AgentManager;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Captures player note-block performances and schedules bounded AI accompaniment. */
public final class AiMusicManager implements AutoCloseable {
	private static final double START_RADIUS = 64.0;
	private static final double PLAY_RADIUS = 96.0;
	private static final int IDLE_TIMEOUT_TICKS = 20 * 60 * 5;
	private static final int MAX_PENDING_NOTES = 128;

	private final AgentManager agents;
	private final Map<UUID, MusicSession> sessions = new HashMap<>();
	private final List<ScheduledNote> pending = new ArrayList<>();

	public AiMusicManager(AgentManager agents) {
		this.agents = agents;
	}

	public void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			BlockState state = level.getBlockState(position);
			if (state.is(Blocks.NOTE_BLOCK)) {
				onPlayerNote(serverPlayer, position, state.getValue(NoteBlock.NOTE));
			}
			return InteractionResult.PASS;
		});
	}

	public synchronized MusicSession start(ServerPlayer owner, List<String> requested, MusicStyle style) {
		List<String> normalized = requested.stream().map(agents::canonicalName).distinct().toList();
		if (normalized.isEmpty() || normalized.size() > MusicSession.MAX_MEMBERS) {
			throw new IllegalArgumentException("合奏需要 1～" + MusicSession.MAX_MEMBERS + " 名 AI");
		}
		Set<String> occupied = occupiedAgentNames(owner.getUUID());
		for (String name : normalized) {
			if (occupied.contains(name.toLowerCase(Locale.ROOT))) {
				throw new IllegalStateException("AI 已在其他合奏中: " + name);
			}
			ServerPlayer agent = agents.managedPlayer(name);
			if (agent.level() != owner.level()) throw new IllegalStateException("AI 与玩家不在同一维度: " + name);
			if (!agent.isAlive() || agent.isRemoved()) throw new IllegalStateException("AI 当前无法合奏: " + name);
			if (agents.isArenaLocked(name)) throw new IllegalStateException("AI 正在参加竞技场: " + name);
			if (agent.distanceToSqr(owner) > START_RADIUS * START_RADIUS) {
				throw new IllegalStateException("AI 距离超过 64 格: " + name);
			}
		}
		long now = owner.level().getServer().getTickCount();
		MusicSession session = new MusicSession(owner.getUUID(), owner.getScoreboardName(), normalized,
			style, now, now, 0);
		sessions.put(owner.getUUID(), session);
		pending.removeIf(note -> note.ownerId.equals(owner.getUUID()));
		return session;
	}

	public synchronized MusicSession status(ServerPlayer owner) {
		MusicSession session = sessions.get(owner.getUUID());
		if (session == null) throw new IllegalStateException("你还没有进行中的 AI 合奏");
		return session;
	}

	public synchronized MusicSession setStyle(ServerPlayer owner, MusicStyle style) {
		MusicSession updated = status(owner).withStyle(style);
		sessions.put(owner.getUUID(), updated);
		return updated;
	}

	public synchronized boolean stop(ServerPlayer owner) {
		pending.removeIf(note -> note.ownerId.equals(owner.getUUID()));
		return sessions.remove(owner.getUUID()) != null;
	}

	public synchronized void onPlayerNote(ServerPlayer owner, BlockPos source, int note) {
		MusicSession session = sessions.get(owner.getUUID());
		if (session == null || !owner.level().getBlockState(source).is(Blocks.NOTE_BLOCK)) return;
		long now = owner.level().getServer().getTickCount();
		if (session.lastNoteTick() == now && session.notesPlayed() > 0) return;
		MusicSession updated = session.afterPlayerNote(now);
		sessions.put(owner.getUUID(), updated);
		for (int index = 0; index < updated.members().size() && pending.size() < MAX_PENDING_NOTES; index++) {
			pending.add(new ScheduledNote(now + updated.style().delayFor(index), owner.getUUID(),
				updated.members().get(index), updated.style().noteFor(Math.clamp(note, 0, 24), index)));
		}
		if (updated.notesPlayed() % 8 == 0) {
			owner.sendSystemMessage(Component.literal("AI 合奏已跟随 " + updated.notesPlayed() + " 个音符"));
		}
	}

	public synchronized void tick(MinecraftServer server) {
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, MusicSession>> sessionIterator = sessions.entrySet().iterator();
		while (sessionIterator.hasNext()) {
			Map.Entry<UUID, MusicSession> entry = sessionIterator.next();
			if (now - entry.getValue().lastNoteTick() <= IDLE_TIMEOUT_TICKS) continue;
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner != null) owner.sendSystemMessage(Component.literal("AI 合奏因 5 分钟未演奏而自动结束"));
			UUID ownerId = entry.getKey();
			sessionIterator.remove();
			pending.removeIf(note -> note.ownerId.equals(ownerId));
		}
		pending.sort(Comparator.comparingLong(ScheduledNote::dueTick));
		Iterator<ScheduledNote> iterator = pending.iterator();
		while (iterator.hasNext()) {
			ScheduledNote scheduled = iterator.next();
			if (scheduled.dueTick > now) break;
			iterator.remove();
			MusicSession session = sessions.get(scheduled.ownerId);
			ServerPlayer owner = server.getPlayerList().getPlayer(scheduled.ownerId);
			if (session == null || owner == null || !session.members().contains(scheduled.agentName)) continue;
			playAgentNote(owner, scheduled);
		}
	}

	private void playAgentNote(ServerPlayer owner, ScheduledNote scheduled) {
		if (!agents.hasAgent(scheduled.agentName) || agents.isArenaLocked(scheduled.agentName)) return;
		ServerPlayer agent = agents.managedPlayer(scheduled.agentName);
		if (agent.level() != owner.level() || agent.distanceToSqr(owner) > PLAY_RADIUS * PLAY_RADIUS
				|| !agent.isAlive() || agent.isRemoved()) return;
		ServerLevel level = agent.level();
		float pitch = (float) Math.pow(2.0, (scheduled.note - 12) / 12.0);
		level.playSound(null, agent.getX(), agent.getY(), agent.getZ(), SoundEvents.NOTE_BLOCK_HARP,
			SoundSource.RECORDS, 0.85F, pitch);
		level.sendParticles(ParticleTypes.NOTE, agent.getX(), agent.getEyeY() + 0.25, agent.getZ(),
			1, scheduled.note / 24.0, 0.0, 0.0, 1.0);
		agent.swing(InteractionHand.MAIN_HAND);
	}

	private Set<String> occupiedAgentNames(UUID exceptOwner) {
		Set<String> result = new HashSet<>();
		for (MusicSession session : sessions.values()) {
			if (session.ownerId().equals(exceptOwner)) continue;
			for (String name : session.members()) result.add(name.toLowerCase(Locale.ROOT));
		}
		return result;
	}

	@Override
	public synchronized void close() {
		pending.clear();
		sessions.clear();
	}

	private record ScheduledNote(long dueTick, UUID ownerId, String agentName, int note) { }
}
