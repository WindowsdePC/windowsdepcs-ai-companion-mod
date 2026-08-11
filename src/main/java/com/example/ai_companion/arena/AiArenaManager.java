package com.example.ai_companion.arena;

import com.example.ai_companion.agent.AgentManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs one server-authoritative AI arena battle and restores all temporary changes afterwards. */
public final class AiArenaManager implements AutoCloseable {
	private static final int DECISION_INTERVAL_TICKS = 5;
	private static final int MATCH_TIMEOUT_TICKS = 20 * 60 * 10;
	private static final int ATTACK_COOLDOWN_TICKS = 12;
	private static final int HEAVY_ATTACK_COOLDOWN_TICKS = 28;
	private static final int POTION_COOLDOWN_TICKS = 20 * 8;
	private static final int COVER_COOLDOWN_TICKS = 20 * 12;
	private static final double START_RADIUS = 5.0;
	private static final double MOVE_STEP = 0.18;
	private static final int MAX_COVER_BLOCKS = 24;

	private final AgentManager agents;
	private Battle battle;

	public AiArenaManager(AgentManager agents) {
		this.agents = agents;
	}

	public synchronized BattleView start(MinecraftServer server, ArenaMode mode, List<String> names) {
		if (battle != null) throw new IllegalStateException("已有 AI 竞技场比赛正在进行");
		ArenaRoster roster = ArenaRoster.create(mode, names);
		List<Participant> participants = new ArrayList<>();
		ServerLevel level = null;
		double centerX = 0;
		double centerY = 0;
		double centerZ = 0;
		for (ArenaRoster.Entry entry : roster.entries()) {
			ServerPlayer player = agents.managedPlayer(entry.name());
			if (!player.isAlive() || player.isRemoved()) {
				throw new IllegalStateException("AI 当前不能参赛: " + entry.name());
			}
			if (level == null) level = player.level();
			if (player.level() != level) throw new IllegalStateException("所有参赛 AI 必须位于同一维度");
			centerX += player.getX();
			centerY += player.getY();
			centerZ += player.getZ();
			participants.add(new Participant(entry, player, Snapshot.capture(player)));
		}
		centerX /= participants.size();
		centerY /= participants.size();
		centerZ /= participants.size();
		Vec3 center = new Vec3(centerX, centerY, centerZ);
		battle = new Battle(roster, level, center, server.getTickCount(), participants);
		try {
			prepareParticipants(battle);
		} catch (RuntimeException error) {
			close();
			throw error;
		}
		broadcast(server, "AI 竞技场开始：" + mode.id() + " · " + String.join(", ", names));
		return view(server.getTickCount());
	}

	public synchronized BattleView view(int currentTick) {
		if (battle == null) return BattleView.idle();
		List<String> active = battle.participants.stream()
			.filter(participant -> !participant.eliminated).map(Participant::name).toList();
		List<String> eliminated = battle.participants.stream()
			.filter(participant -> participant.eliminated).map(Participant::name).toList();
		return new BattleView(true, battle.roster.mode().id(), active, eliminated,
			Math.max(0, currentTick - battle.startedAtTick) / 20);
	}

	public synchronized boolean stop(MinecraftServer server, String reason) {
		if (battle == null) return false;
		finish(server, reason == null || reason.isBlank() ? "比赛已由管理员停止" : reason);
		return true;
	}

	public synchronized void tick(MinecraftServer server) {
		if (battle == null) return;
		int now = server.getTickCount();
		if (now - battle.startedAtTick >= MATCH_TIMEOUT_TICKS) {
			finish(server, "比赛超过 10 分钟，结果为平局");
			return;
		}
		updateEliminations(server);
		if (battle == null || finishIfDecided(server)) return;
		if (now % DECISION_INTERVAL_TICKS != 0) return;

		for (Participant participant : battle.participants) {
			if (participant.eliminated) continue;
			Participant enemy = nearestEnemy(participant);
			if (enemy == null) continue;
			act(participant, enemy, now);
		}
		updateEliminations(server);
		if (battle != null) finishIfDecided(server);
	}

	private void prepareParticipants(Battle active) {
		for (int index = 0; index < active.participants.size(); index++) {
			Participant participant = active.participants.get(index);
			agents.setArenaLocked(participant.name(), true);
			double angle = Math.PI * 2.0 * index / active.participants.size();
			participant.player.setPos(active.center.x + Math.cos(angle) * START_RADIUS,
				active.center.y, active.center.z + Math.sin(angle) * START_RADIUS);
			participant.player.setHealth(participant.player.getMaxHealth());
			participant.player.setInvulnerable(false);
			participant.player.stopUsingItem();
			participant.player.setItemSlot(EquipmentSlot.MAINHAND, Items.IRON_SWORD.getDefaultInstance());
			participant.player.setItemSlot(EquipmentSlot.OFFHAND, Items.SHIELD.getDefaultInstance());
			face(participant.player, active.center.x, active.center.z);
		}
	}

	private void act(Participant participant, Participant enemy, int now) {
		ServerPlayer player = participant.player;
		ServerPlayer target = enemy.player;
		double distance = horizontalDistance(player, target);
		float healthRatio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());

		if (healthRatio <= 0.38F && participant.healingPotions > 0
				&& now - participant.lastPotionTick >= POTION_COOLDOWN_TICKS) {
			participant.healingPotions--;
			participant.lastPotionTick = now;
			player.stopUsingItem();
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 1));
			return;
		}
		if (healthRatio <= 0.55F && now - participant.lastCoverTick >= COVER_COOLDOWN_TICKS) {
			participant.lastCoverTick = now;
			if (buildTemporaryCover(player, target)) {
				player.startUsingItem(InteractionHand.OFF_HAND);
				retreat(player, target);
				return;
			}
		}

		face(player, target.getX(), target.getZ());
		if (distance > 3.0) {
			player.stopUsingItem();
			advance(player, target, distance > 8.0 ? MOVE_STEP : MOVE_STEP * 0.75);
			return;
		}
		if (healthRatio < 0.45F && distance < 1.8) {
			player.startUsingItem(InteractionHand.OFF_HAND);
			retreat(player, target);
			return;
		}

		player.stopUsingItem();
		int cooldown = participant.attackNumber % 3 == 2
			? HEAVY_ATTACK_COOLDOWN_TICKS : ATTACK_COOLDOWN_TICKS;
		if (now - participant.lastAttackTick < cooldown) return;
		participant.lastAttackTick = now;
		participant.attackNumber++;
		player.swing(InteractionHand.MAIN_HAND);
		float damage = participant.attackNumber % 3 == 0 ? 6.0F : 4.0F;
		target.setHealth(Math.max(1.0F, target.getHealth() - damage));
	}

	private boolean buildTemporaryCover(ServerPlayer player, ServerPlayer enemy) {
		if (battle.coverBlocks.size() >= MAX_COVER_BLOCKS) return false;
		double distance = Math.max(0.01, horizontalDistance(player, enemy));
		double directionX = (enemy.getX() - player.getX()) / distance;
		double directionZ = (enemy.getZ() - player.getZ()) / distance;
		// Place cover beside the defender rather than inside the direct pursuit path. It remains
		// visually useful without trapping the simple movement controller behind its own wall.
		BlockPos base = BlockPos.containing(player.getX() - directionZ * 1.4,
			player.getY(), player.getZ() + directionX * 1.4);
		boolean placed = false;
		for (int y = 0; y < 2 && battle.coverBlocks.size() < MAX_COVER_BLOCKS; y++) {
			BlockPos position = base.above(y);
			BlockState previous = battle.level.getBlockState(position);
			if (!previous.isAir() || battle.coverBlocks.containsKey(position)) continue;
			battle.coverBlocks.put(position.immutable(), previous);
			battle.level.setBlockAndUpdate(position, Blocks.COBBLESTONE.defaultBlockState());
			placed = true;
		}
		return placed;
	}

	private void updateEliminations(MinecraftServer server) {
		for (Participant participant : battle.participants) {
			if (participant.eliminated) continue;
			if (participant.player.isRemoved() || !participant.player.isAlive()
					|| participant.player.getHealth() <= 1.01F) {
				participant.eliminated = true;
				participant.player.stopUsingItem();
				participant.player.setInvulnerable(true);
				participant.player.setHealth(Math.max(1.0F, participant.player.getMaxHealth()));
				broadcast(server, participant.name() + " 已被淘汰");
			}
		}
	}

	private boolean finishIfDecided(MinecraftServer server) {
		Set<String> eliminated = new HashSet<>();
		for (Participant participant : battle.participants) {
			if (participant.eliminated) eliminated.add(participant.name());
		}
		Set<Integer> teams = battle.roster.activeTeams(eliminated);
		if (teams.size() > 1) return false;
		if (teams.isEmpty()) {
			finish(server, "所有参赛 AI 同时被淘汰，比赛平局");
		} else {
			int winner = teams.iterator().next();
			List<String> winners = battle.participants.stream()
				.filter(participant -> participant.entry.team() == winner && !participant.eliminated)
				.map(Participant::name).toList();
			finish(server, "获胜：" + String.join(", ", winners));
		}
		return true;
	}

	private Participant nearestEnemy(Participant participant) {
		Participant nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Participant candidate : battle.participants) {
			if (candidate.eliminated || candidate.entry.team() == participant.entry.team()) continue;
			double distance = horizontalDistance(participant.player, candidate.player);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private void finish(MinecraftServer server, String result) {
		Battle finished = battle;
		battle = null;
		restoreBattle(finished);
		broadcast(server, "AI 竞技场结束：" + result);
	}

	private void restoreBattle(Battle finished) {
		for (Map.Entry<BlockPos, BlockState> cover : finished.coverBlocks.entrySet()) {
			// Preserve a block another player deliberately placed during the match.
			if (finished.level.getBlockState(cover.getKey()).is(Blocks.COBBLESTONE)) {
				finished.level.setBlockAndUpdate(cover.getKey(), cover.getValue());
			}
		}
		for (Participant participant : finished.participants) {
			participant.snapshot.restore(participant.player);
			try {
				agents.setArenaLocked(participant.name(), false);
			} catch (IllegalArgumentException ignored) {
				// The AI may have been removed while the match was running.
			}
		}
	}

	private static void advance(ServerPlayer player, ServerPlayer enemy, double step) {
		double distance = Math.max(0.01, horizontalDistance(player, enemy));
		player.move(MoverType.SELF, new Vec3((enemy.getX() - player.getX()) / distance * step,
			0, (enemy.getZ() - player.getZ()) / distance * step));
	}

	private static void retreat(ServerPlayer player, ServerPlayer enemy) {
		double distance = Math.max(0.01, horizontalDistance(player, enemy));
		player.move(MoverType.SELF, new Vec3((player.getX() - enemy.getX()) / distance * MOVE_STEP,
			0, (player.getZ() - enemy.getZ()) / distance * MOVE_STEP));
	}

	private static double horizontalDistance(ServerPlayer first, ServerPlayer second) {
		return Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
	}

	private static void face(ServerPlayer player, double targetX, double targetZ) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(targetX - player.getX()), targetZ - player.getZ()));
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
	}

	private static void broadcast(MinecraftServer server, String message) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}

	@Override
	public synchronized void close() {
		if (battle == null) return;
		Battle abandoned = battle;
		battle = null;
		restoreBattle(abandoned);
	}

	private static final class Battle {
		final ArenaRoster roster;
		final ServerLevel level;
		final Vec3 center;
		final int startedAtTick;
		final List<Participant> participants;
		final Map<BlockPos, BlockState> coverBlocks = new LinkedHashMap<>();

		Battle(ArenaRoster roster, ServerLevel level, Vec3 center, int startedAtTick,
				List<Participant> participants) {
			this.roster = roster;
			this.level = level;
			this.center = center;
			this.startedAtTick = startedAtTick;
			this.participants = participants;
		}
	}

	private static final class Participant {
		final ArenaRoster.Entry entry;
		final ServerPlayer player;
		final Snapshot snapshot;
		boolean eliminated;
		int healingPotions = 2;
		int lastPotionTick = Integer.MIN_VALUE / 2;
		int lastCoverTick = Integer.MIN_VALUE / 2;
		int lastAttackTick = Integer.MIN_VALUE / 2;
		int attackNumber;

		Participant(ArenaRoster.Entry entry, ServerPlayer player, Snapshot snapshot) {
			this.entry = entry;
			this.player = player;
			this.snapshot = snapshot;
		}

		String name() {
			return entry.name();
		}
	}

	private record Snapshot(double x, double y, double z, float health, boolean invulnerable,
			ItemStack mainHand, ItemStack offHand) {
		static Snapshot capture(ServerPlayer player) {
			return new Snapshot(player.getX(), player.getY(), player.getZ(), player.getHealth(),
				player.isInvulnerable(), player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
				player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
		}

		void restore(ServerPlayer player) {
			if (player.isRemoved()) return;
			player.stopUsingItem();
			player.removeEffect(MobEffects.REGENERATION);
			player.setInvulnerable(invulnerable);
			player.setItemSlot(EquipmentSlot.MAINHAND, mainHand.copy());
			player.setItemSlot(EquipmentSlot.OFFHAND, offHand.copy());
			player.setHealth(Math.clamp(health, 1.0F, player.getMaxHealth()));
			player.setPos(x, y, z);
		}
	}

	public record BattleView(boolean active, String mode, List<String> activeParticipants,
			List<String> eliminatedParticipants, int elapsedSeconds) {
		static BattleView idle() {
			return new BattleView(false, "", List.of(), List.of(), 0);
		}

		public String displayText() {
			if (!active) return "当前没有进行中的 AI 竞技场比赛";
			String eliminated = eliminatedParticipants.isEmpty() ? "无"
				: String.join(", ", eliminatedParticipants);
			return "模式=" + mode + "，进行=" + elapsedSeconds + "秒，仍在场="
				+ String.join(", ", activeParticipants) + "，已淘汰=" + eliminated;
		}
	}
}
