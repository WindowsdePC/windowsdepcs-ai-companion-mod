package com.example.ai_companion.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Minecraft 1.20.1 Fabric implementation of the server-authoritative AI core. */
public final class LegacyFabricMod implements ModInitializer {
	public static final String MOD_ID = "ai_companion";
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DATA_FILE = net.fabricmc.loader.api.FabricLoader.getInstance()
		.getConfigDir().resolve("ai_companion-1.20.1.json");
	private static final Map<String, RuntimeAgent> AGENTS = new LinkedHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15)).build();
	private static volatile State state = new State();
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("aiplayer")
				.then(literal("create").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::create)))
				.then(literal("remove").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::remove)))
				.then(literal("list").executes(LegacyFabricMod::list))
				.then(literal("positions").executes(LegacyFabricMod::positions))
				.then(literal("idle").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word()).executes(ctx -> mode(ctx, "idle"))))
				.then(literal("hunt").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word())
						.then(argument("target", StringArgumentType.word()).executes(ctx -> mode(ctx, "hunter")))))
				.then(literal("team").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word())
						.then(argument("target", StringArgumentType.word()).executes(ctx -> mode(ctx, "teammate")))))
				.then(literal("eye").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::eye)))
				.then(literal("ask").requires(source -> source.hasPermission(2))
					.then(argument("name", StringArgumentType.word())
						.then(argument("message", StringArgumentType.greedyString()).executes(LegacyFabricMod::ask))))
				.then(literal("config").requires(source -> source.hasPermission(2))
					.then(literal("status").executes(LegacyFabricMod::configStatus))
					.then(literal("endpoint").then(argument("url", StringArgumentType.greedyString())
						.executes(ctx -> setConfig(ctx, "endpoint"))))
					.then(literal("model").then(argument("model", StringArgumentType.greedyString())
						.executes(ctx -> setConfig(ctx, "model"))))
					.then(literal("token").then(argument("token", StringArgumentType.greedyString())
						.executes(ctx -> setConfig(ctx, "token")))))
				.then(literal("pet")
					.then(literal("create").then(argument("name", StringArgumentType.word())
						.then(argument("speed", IntegerArgumentType.integer(10, 100))
							.then(argument("strength", IntegerArgumentType.integer(10, 100))
								.then(argument("endurance", IntegerArgumentType.integer(10, 100))
									.executes(LegacyFabricMod::petCreate))))))
					.then(literal("list").executes(LegacyFabricMod::petList))
					.then(literal("train").then(argument("name", StringArgumentType.word())
						.then(argument("attribute", StringArgumentType.word()).executes(LegacyFabricMod::petTrain))))
					.then(literal("race").then(argument("first", StringArgumentType.word())
						.then(argument("second", StringArgumentType.word()).executes(ctx -> petCompete(ctx, false)))))
					.then(literal("battle").then(argument("first", StringArgumentType.word())
						.then(argument("second", StringArgumentType.word()).executes(ctx -> petCompete(ctx, true)))))
					.then(literal("leaderboard").executes(LegacyFabricMod::petLeaderboard)))
				.then(literal("society")
					.then(literal("enroll").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::societyEnroll)))
					.then(literal("home").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::societyHome)))
					.then(literal("job").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).then(argument("job", StringArgumentType.word())
							.executes(LegacyFabricMod::societyJob))))
					.then(literal("work").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::societyWork)))
					.then(literal("rest").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::societyRest)))
					.then(literal("socialize").requires(source -> source.hasPermission(2))
						.then(argument("name", StringArgumentType.word()).then(argument("other", StringArgumentType.word())
							.executes(LegacyFabricMod::societySocialize))))
					.then(literal("trade").requires(source -> source.hasPermission(2))
						.then(argument("seller", StringArgumentType.word()).then(argument("buyer", StringArgumentType.word())
							.then(argument("amount", IntegerArgumentType.integer(1, 1_000_000))
								.executes(LegacyFabricMod::societyTrade)))))
					.then(literal("status").then(argument("name", StringArgumentType.word()).executes(LegacyFabricMod::societyStatus)))
					.then(literal("leaderboard").executes(LegacyFabricMod::societyLeaderboard)))
				.then(literal("compatibility").executes(LegacyFabricMod::compatibility))));

		ServerLifecycleEvents.SERVER_STARTED.register(LegacyFabricMod::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> save());
	}

	private static void start(MinecraftServer minecraftServer) {
		server = minecraftServer;
		load();
		for (AgentData data : new ArrayList<>(state.agents.values())) restore(data);
	}

	private static int create(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		if (!NAME.matcher(name).matches()) return fail(context, "AI 名称必须为 3～16 位字母、数字或下划线");
		String key = name.toLowerCase(Locale.ROOT);
		if (state.agents.containsKey(key)) return fail(context, "AI 已存在：" + name);
		ServerPlayer owner;
		try {
			owner = context.getSource().getPlayerOrException();
		} catch (Exception exception) {
			return fail(context, "该命令需要由游戏内玩家执行");
		}
		UUID uuid = UUID.nameUUIDFromBytes(("ai_companion:" + key).getBytes(StandardCharsets.UTF_8));
		AgentData data = new AgentData(name, uuid.toString(), owner.level().dimension().location().toString(),
			owner.getX() + 1.0, owner.getY(), owner.getZ() + 1.0, "idle", "");
		state.agents.put(key, data);
		restore(data);
		save();
		return ok(context, "已创建 1.20.1 Fabric AI：" + name);
	}

	private static int remove(CommandContext<CommandSourceStack> context) {
		String key = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
		AgentData removed = state.agents.remove(key);
		RuntimeAgent runtime = AGENTS.remove(key);
		if (removed == null) return fail(context, "未找到 AI");
		if (runtime != null) runtime.player.remove(Entity.RemovalReason.DISCARDED);
		save();
		return ok(context, "已移除 AI：" + removed.name);
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		if (state.agents.isEmpty()) return ok(context, "当前没有 AI");
		return ok(context, "AI（" + state.agents.size() + "）：" + String.join(", ",
			state.agents.values().stream().map(data -> data.name + "[" + data.mode + "]").toList()));
	}

	private static int positions(CommandContext<CommandSourceStack> context) {
		if (AGENTS.isEmpty()) return ok(context, "当前没有已加载的 AI");
		for (RuntimeAgent runtime : AGENTS.values()) {
			ServerPlayer player = runtime.player;
			context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"%s · %s · X %.1f, Y %.1f, Z %.1f", runtime.data.name,
				player.level().dimension().location(), player.getX(), player.getY(), player.getZ())), false);
		}
		return AGENTS.size();
	}

	private static int mode(CommandContext<CommandSourceStack> context, String selected) {
		AgentData data = find(context);
		if (data == null) return 0;
		String target = selected.equals("idle") ? "" : StringArgumentType.getString(context, "target");
		if (!target.isBlank() && server.getPlayerList().getPlayerByName(target) == null) {
			return fail(context, "目标玩家不在线：" + target);
		}
		data.mode = selected;
		data.target = target;
		save();
		return ok(context, "已设置 " + data.name + " 为 " + selected + (target.isBlank() ? "" : "，目标 " + target));
	}

	private static int eye(CommandContext<CommandSourceStack> context) {
		AgentData data = find(context);
		if (data == null) return 0;
		if (data.target.isBlank()) return fail(context, "该 AI 没有目标玩家");
		ServerPlayer target = server.getPlayerList().getPlayerByName(data.target);
		if (target == null) return fail(context, "目标玩家当前不在线");
		return ok(context, String.format(Locale.ROOT, "天眼快照：%s · %s · X %.1f, Y %.1f, Z %.1f",
			target.getGameProfile().getName(), target.level().dimension().location(), target.getX(), target.getY(), target.getZ()));
	}

	private static int ask(CommandContext<CommandSourceStack> context) {
		AgentData data = find(context);
		if (data == null) return 0;
		RuntimeAgent runtime = AGENTS.get(data.name.toLowerCase(Locale.ROOT));
		if (runtime == null) return fail(context, "AI 尚未加载");
		if (runtime.thinking) return fail(context, "该 AI 正在思考");
		String token = effectiveToken();
		if (token.isBlank()) return fail(context, "尚未配置 API 令牌");
		String message = StringArgumentType.getString(context, "message");
		if (message.length() > 1000) return fail(context, "消息不能超过 1000 字符");
		runtime.thinking = true;
		ok(context, data.name + " 正在思考…");
		requestDecision(data, runtime.player, message, token).whenComplete((decision, error) -> server.execute(() -> {
			runtime.thinking = false;
			if (error != null) {
				context.getSource().sendFailure(Component.literal("AI 请求失败：" + safeError(error)));
				return;
			}
			execute(runtime.player, decision);
			context.getSource().sendSuccess(() -> Component.literal(data.name + " 执行：" + decision.action), false);
		}));
		return 1;
	}

	private static CompletableFuture<Decision> requestDecision(AgentData data, ServerPlayer player, String message, String token) {
		JsonObject body = new JsonObject();
		body.addProperty("model", state.model);
		body.addProperty("max_tokens", 300);
		JsonArray messages = new JsonArray();
		messages.add(chat("system", systemPrompt(data)));
		messages.add(chat("user", "位置：" + player.level().dimension().location() + " "
			+ Math.round(player.getX()) + "," + Math.round(player.getY()) + "," + Math.round(player.getZ())
			+ "\n玩家指令：" + message));
		body.add("messages", messages);
		HttpRequest request = HttpRequest.newBuilder(endpoint(state.endpoint))
			.timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
				.getAsJsonObject("message").get("content").getAsString().strip()
				.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
			Decision parsed = GSON.fromJson(content, Decision.class);
			return parsed == null ? new Decision("wait", "", 0, 0) : parsed.safe();
		});
	}

	private static void execute(ServerPlayer player, Decision decision) {
		switch (decision.action) {
			case "say" -> {
				if (!decision.say.isBlank()) server.getPlayerList().broadcastSystemMessage(
					Component.literal("<" + player.getGameProfile().getName() + "> " + decision.say), false);
			}
			case "move" -> player.moveTo(player.getX() + decision.dx, player.getY(), player.getZ() + decision.dz,
				player.getYRot(), player.getXRot());
			default -> { }
		}
	}

	private static int configStatus(CommandContext<CommandSourceStack> context) {
		return ok(context, "API=" + state.endpoint + "，模型=" + state.model + "，令牌="
			+ (effectiveToken().isBlank() ? "未配置" : "已配置"));
	}

	private static int setConfig(CommandContext<CommandSourceStack> context, String field) {
		String value = StringArgumentType.getString(context, field.equals("endpoint") ? "url" : field).strip();
		if (value.isBlank() || value.length() > 2048) return fail(context, "配置值无效");
		switch (field) {
			case "endpoint" -> state.endpoint = value;
			case "model" -> state.model = value;
			case "token" -> state.token = value;
			default -> { return 0; }
		}
		save();
		return ok(context, field.equals("token") ? "API 令牌已保存到服务端配置" : "配置已更新");
	}

	private static int compatibility(CommandContext<CommandSourceStack> context) {
		return ok(context, "AI Companion 0.8.2 · Minecraft 1.20.1 Fabric：AI 核心、宠物竞技与模拟社会可用");
	}

	private static int petCreate(CommandContext<CommandSourceStack> context) {
		ServerPlayer owner;
		try { owner = context.getSource().getPlayerOrException(); }
		catch (Exception error) { return fail(context, "该命令需要由游戏内玩家执行"); }
		String name = StringArgumentType.getString(context, "name");
		String key = name.toLowerCase(Locale.ROOT);
		int speed = IntegerArgumentType.getInteger(context, "speed");
		int strength = IntegerArgumentType.getInteger(context, "strength");
		int endurance = IntegerArgumentType.getInteger(context, "endurance");
		if (name.length() > 24) return fail(context, "宠物名称不能超过 24 字符");
		if (speed + strength + endurance > 180) return fail(context, "初始属性总和不能超过 180");
		if (state.pets.containsKey(key)) return fail(context, "宠物名称已存在");
		if (state.pets.values().stream().filter(pet -> pet.ownerId.equals(owner.getUUID().toString())).count() >= 8) {
			return fail(context, "每位玩家最多拥有 8 只竞技宠物");
		}
		state.pets.put(key, new PetData(name, owner.getUUID().toString(), owner.getScoreboardName(),
			speed, strength, endurance));
		save();
		return ok(context, "已创建竞技宠物：" + petSummary(state.pets.get(key)));
	}

	private static int petList(CommandContext<CommandSourceStack> context) {
		ServerPlayer owner;
		try { owner = context.getSource().getPlayerOrException(); }
		catch (Exception error) { return fail(context, "该命令需要由游戏内玩家执行"); }
		var owned = state.pets.values().stream().filter(pet -> pet.ownerId.equals(owner.getUUID().toString()))
			.sorted(Comparator.comparing(pet -> pet.name.toLowerCase(Locale.ROOT))).toList();
		if (owned.isEmpty()) return ok(context, "你还没有竞技宠物");
		owned.forEach(pet -> context.getSource().sendSuccess(() -> Component.literal(petSummary(pet)), false));
		return owned.size();
	}

	private static int petTrain(CommandContext<CommandSourceStack> context) {
		ServerPlayer owner;
		try { owner = context.getSource().getPlayerOrException(); }
		catch (Exception error) { return fail(context, "该命令需要由游戏内玩家执行"); }
		PetData pet = state.pets.get(StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT));
		if (pet == null) return fail(context, "未找到宠物");
		if (!pet.ownerId.equals(owner.getUUID().toString())) return fail(context, "只能训练自己的宠物");
		long now = System.currentTimeMillis();
		if (now < pet.lastTrainingMillis + 30_000L) return fail(context, "训练冷却尚未结束");
		String attribute = StringArgumentType.getString(context, "attribute").toLowerCase(Locale.ROOT);
		switch (attribute) {
			case "speed" -> { if (pet.speed >= 100) return fail(context, "速度已达上限"); pet.speed++; }
			case "strength" -> { if (pet.strength >= 100) return fail(context, "力量已达上限"); pet.strength++; }
			case "endurance" -> { if (pet.endurance >= 100) return fail(context, "耐力已达上限"); pet.endurance++; }
			default -> { return fail(context, "属性必须是 speed、strength 或 endurance"); }
		}
		pet.lastTrainingMillis = now; pet.trainingCount++; save();
		return ok(context, "训练完成：" + petSummary(pet));
	}

	private static int petCompete(CommandContext<CommandSourceStack> context, boolean battle) {
		PetData first = state.pets.get(StringArgumentType.getString(context, "first").toLowerCase(Locale.ROOT));
		PetData second = state.pets.get(StringArgumentType.getString(context, "second").toLowerCase(Locale.ROOT));
		if (first == null || second == null) return fail(context, "未找到参赛宠物");
		if (first == second) return fail(context, "参赛宠物不能相同");
		SplittableRandom random = new SplittableRandom(server.getTickCount() ^ System.nanoTime());
		int firstScore = (battle ? first.strength * 5 + first.endurance * 3 + first.speed
			: first.speed * 5 + first.endurance * 3 + first.strength) + random.nextInt(61);
		int secondScore = (battle ? second.strength * 5 + second.endurance * 3 + second.speed
			: second.speed * 5 + second.endurance * 3 + second.strength) + random.nextInt(61);
		if (firstScore == secondScore) { firstScore += first.endurance >= second.endurance ? 1 : 0;
			secondScore += first.endurance < second.endurance ? 1 : 0; }
		PetData winner = firstScore > secondScore ? first : second;
		PetData loser = winner == first ? second : first;
		winner.wins++; loser.losses++; winner.races++; loser.races++; save();
		return ok(context, (battle ? "战斗" : "竞速") + "结果：" + winner.name + " 战胜 " + loser.name
			+ "（" + Math.max(firstScore, secondScore) + ":" + Math.min(firstScore, secondScore) + "）");
	}

	private static int petLeaderboard(CommandContext<CommandSourceStack> context) {
		var board = state.pets.values().stream().sorted(Comparator.comparingInt(LegacyFabricMod::petRating)
			.reversed().thenComparing(pet -> pet.name.toLowerCase(Locale.ROOT))).limit(10).toList();
		if (board.isEmpty()) return ok(context, "竞技排行榜暂无记录");
		for (int i = 0; i < board.size(); i++) {
			PetData pet = board.get(i); int rank = i + 1;
			context.getSource().sendSuccess(() -> Component.literal("#" + rank + " " + pet.name + " · 主人="
				+ pet.ownerName + " · 胜负=" + pet.wins + "/" + pet.losses + " · 评分=" + petRating(pet)), false);
		}
		return board.size();
	}

	private static int petRating(PetData pet) { return pet.speed + pet.strength + pet.endurance + pet.wins * 3 - pet.losses; }
	private static String petSummary(PetData pet) { return pet.name + " · 速度=" + pet.speed + " · 力量="
		+ pet.strength + " · 耐力=" + pet.endurance + " · 胜负=" + pet.wins + "/" + pet.losses; }

	private static int societyEnroll(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		String key = name.toLowerCase(Locale.ROOT);
		AgentData agent = state.agents.get(key);
		if (agent == null) return fail(context, "未找到已登记 AI：" + name);
		if (state.society.containsKey(key)) return fail(context, "AI 已加入模拟社会");
		if (state.society.size() >= 128) return fail(context, "模拟社会最多容纳 128 名 AI");
		state.society.put(key, new SocietyData(agent.name)); save();
		return ok(context, "已加入模拟社会：" + agent.name);
	}

	private static int societyHome(CommandContext<CommandSourceStack> context) {
		SocietyData data = societyFind(context, "name"); if (data == null) return 0;
		ServerPlayer player;
		try { player = context.getSource().getPlayerOrException(); }
		catch (Exception error) { return fail(context, "该命令需要由游戏内玩家执行"); }
		data.homeDimension = player.level().dimension().location().toString();
		data.homeX = player.getX(); data.homeY = player.getY(); data.homeZ = player.getZ(); save();
		return ok(context, data.agentName + " 的住宅已设置在当前位置");
	}

	private static int societyJob(CommandContext<CommandSourceStack> context) {
		SocietyData data = societyFind(context, "name"); if (data == null) return 0;
		String job = StringArgumentType.getString(context, "job").toLowerCase(Locale.ROOT);
		if (societyWage(job) < 0) return fail(context, "职业必须是 farmer、builder、miner、merchant、guard 或 artist");
		data.job = job; save(); return ok(context, data.agentName + " 的工作已设为 " + job);
	}

	private static int societyWork(CommandContext<CommandSourceStack> context) {
		SocietyData data = societyFind(context, "name"); if (data == null) return 0;
		if (data.homeDimension.isBlank()) return fail(context, "请先为 AI 设置住宅");
		int wage = societyWage(data.job); if (wage <= 0) return fail(context, "请先为 AI 分配工作");
		if (data.energy < 15) return fail(context, "AI 精力不足，需要休息");
		long now = System.currentTimeMillis();
		if (now < data.lastWorkMillis + 60_000L) return fail(context, "工作冷却尚未结束");
		data.balance += wage; data.energy -= 15; data.reputation++; data.lastWorkMillis = now; save();
		return ok(context, data.agentName + " 完成工作，获得 " + wage + " 信用点，余额=" + data.balance);
	}

	private static int societyRest(CommandContext<CommandSourceStack> context) {
		SocietyData data = societyFind(context, "name"); if (data == null) return 0;
		data.energy = 100; save(); return ok(context, data.agentName + " 已休息并恢复精力");
	}

	private static int societySocialize(CommandContext<CommandSourceStack> context) {
		SocietyData first = societyFind(context, "name");
		SocietyData second = societyFind(context, "other");
		if (first == null || second == null) return 0;
		if (first == second) return fail(context, "AI 不能与自己社交");
		first.relations.put(second.agentName.toLowerCase(Locale.ROOT), Math.min(100,
			first.relations.getOrDefault(second.agentName.toLowerCase(Locale.ROOT), 0) + 5));
		second.relations.put(first.agentName.toLowerCase(Locale.ROOT), Math.min(100,
			second.relations.getOrDefault(first.agentName.toLowerCase(Locale.ROOT), 0) + 5));
		first.energy = Math.max(0, first.energy - 3); second.energy = Math.max(0, second.energy - 3);
		first.reputation++; second.reputation++; save();
		return ok(context, first.agentName + " 与 " + second.agentName + " 完成社交，关系值="
			+ first.relations.get(second.agentName.toLowerCase(Locale.ROOT)));
	}

	private static int societyTrade(CommandContext<CommandSourceStack> context) {
		SocietyData seller = societyFind(context, "seller"); SocietyData buyer = societyFind(context, "buyer");
		if (seller == null || buyer == null) return 0;
		if (seller == buyer) return fail(context, "交易双方不能相同");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		if (seller.balance < amount) return fail(context, "AI 余额不足");
		seller.balance -= amount; buyer.balance += amount; save();
		return ok(context, seller.agentName + " 已向 " + buyer.agentName + " 支付 " + amount + " 信用点");
	}

	private static int societyStatus(CommandContext<CommandSourceStack> context) {
		SocietyData data = societyFind(context, "name"); return data == null ? 0 : ok(context, societySummary(data));
	}

	private static int societyLeaderboard(CommandContext<CommandSourceStack> context) {
		var board = state.society.values().stream().sorted(Comparator.comparingLong((SocietyData data) -> data.balance)
			.reversed().thenComparing(Comparator.comparingInt((SocietyData data) -> data.reputation).reversed())
			.thenComparing(data -> data.agentName.toLowerCase(Locale.ROOT))).limit(10).toList();
		if (board.isEmpty()) return ok(context, "模拟社会暂无居民");
		for (int i = 0; i < board.size(); i++) { SocietyData data = board.get(i); int rank = i + 1;
			context.getSource().sendSuccess(() -> Component.literal("#" + rank + " " + societySummary(data)), false); }
		return board.size();
	}

	private static SocietyData societyFind(CommandContext<CommandSourceStack> context, String argumentName) {
		String name = StringArgumentType.getString(context, argumentName);
		SocietyData data = state.society.get(name.toLowerCase(Locale.ROOT));
		if (data == null) fail(context, "AI 尚未加入模拟社会：" + name); return data;
	}

	private static int societyWage(String job) { return switch (job) {
		case "farmer" -> 12; case "builder" -> 16; case "miner" -> 18; case "merchant" -> 20;
		case "guard" -> 15; case "artist" -> 14; case "unemployed" -> 0; default -> -1; }; }
	private static String societySummary(SocietyData data) { return data.agentName + " · 住宅="
		+ (data.homeDimension.isBlank() ? "未设置" : data.homeDimension) + " · 工作=" + data.job
		+ " · 余额=" + data.balance + " · 精力=" + data.energy + " · 声望=" + data.reputation; }

	private static AgentData find(CommandContext<CommandSourceStack> context) {
		String key = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
		AgentData data = state.agents.get(key);
		if (data == null) fail(context, "未找到 AI：" + key);
		return data;
	}

	private static void restore(AgentData data) {
		try {
			ResourceLocation id = new ResourceLocation(data.dimension);
			ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
			ServerLevel level = server.getLevel(key);
			if (level == null) level = server.overworld();
			FakePlayer fake = FakePlayer.get(level, new GameProfile(UUID.fromString(data.uuid), data.name));
			fake.moveTo(data.x, data.y, data.z, 0, 0);
			level.addNewPlayer(fake);
			AGENTS.put(data.name.toLowerCase(Locale.ROOT), new RuntimeAgent(data, fake));
		} catch (RuntimeException exception) {
			System.err.println("[AI Companion] 无法恢复 AI " + data.name + ": " + safeError(exception));
		}
	}

	private static JsonObject chat(String role, String content) {
		JsonObject value = new JsonObject();
		value.addProperty("role", role);
		value.addProperty("content", content);
		return value;
	}

	private static URI endpoint(String base) {
		String normalized = base.strip().replaceAll("/+$", "");
		return URI.create(normalized.endsWith("/chat/completions") ? normalized : normalized + "/chat/completions");
	}

	private static String systemPrompt(AgentData data) {
		return "你是 Minecraft 1.20.1 中的真实 AI 玩家。模式=" + data.mode + "，目标="
			+ (data.target.isBlank() ? "未指定" : data.target) + "。禁止命令、传送、创造模式、复制物品和未经提供的透视信息。"
			+ "每次只返回 JSON：{\"action\":\"say|move|wait\",\"say\":\"最多240字符\",\"dx\":-8到8,\"dz\":-8到8}";
	}

	private static String effectiveToken() {
		String environment = System.getenv("MCAI_API_KEY");
		return environment == null || environment.isBlank() ? state.token : environment.strip();
	}

	private static void load() {
		if (!Files.isRegularFile(DATA_FILE)) return;
		try {
			State loaded = GSON.fromJson(Files.readString(DATA_FILE), State.class);
			if (loaded != null) state = loaded.normalized();
		} catch (Exception exception) {
			System.err.println("[AI Companion] 配置读取失败：" + safeError(exception));
		}
	}

	private static synchronized void save() {
		for (RuntimeAgent runtime : AGENTS.values()) {
			ServerPlayer player = runtime.player;
			runtime.data.dimension = player.level().dimension().location().toString();
			runtime.data.x = player.getX(); runtime.data.y = player.getY(); runtime.data.z = player.getZ();
		}
		try {
			Files.createDirectories(DATA_FILE.getParent());
			Path temporary = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
			Files.move(temporary, DATA_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException exception) {
			System.err.println("[AI Companion] 配置保存失败：" + safeError(exception));
		}
	}

	private static int ok(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendFailure(Component.literal(message));
		return 0;
	}

	private static String safeError(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		String message = current.getMessage();
		return message == null ? current.getClass().getSimpleName() : message.substring(0, Math.min(300, message.length()));
	}

	private static final class RuntimeAgent {
		private final AgentData data;
		private final FakePlayer player;
		private boolean thinking;
		private RuntimeAgent(AgentData data, FakePlayer player) { this.data = data; this.player = player; }
	}

	private static final class State {
		private String endpoint = "https://api.openai.com/v1";
		private String model = "gpt-5-mini";
		private String token = "";
		private Map<String, AgentData> agents = new LinkedHashMap<>();
		private Map<String, PetData> pets = new LinkedHashMap<>();
		private Map<String, SocietyData> society = new LinkedHashMap<>();
		private State normalized() {
			if (endpoint == null || endpoint.isBlank()) endpoint = "https://api.openai.com/v1";
			if (model == null || model.isBlank()) model = "gpt-5-mini";
			if (token == null) token = "";
			if (agents == null) agents = new LinkedHashMap<>();
			if (pets == null) pets = new LinkedHashMap<>();
			if (society == null) society = new LinkedHashMap<>();
			return this;
		}
	}

	private static final class SocietyData {
		private String agentName, homeDimension = "", job = "unemployed";
		private double homeX, homeY, homeZ;
		private long balance, lastWorkMillis;
		private int energy = 100, reputation;
		private Map<String, Integer> relations = new LinkedHashMap<>();
		private SocietyData(String agentName) { this.agentName = agentName; }
	}

	private static final class PetData {
		private String name, ownerId, ownerName;
		private int speed, strength, endurance, wins, losses, races;
		private long trainingCount, lastTrainingMillis;
		private PetData(String name, String ownerId, String ownerName, int speed, int strength, int endurance) {
			this.name = name; this.ownerId = ownerId; this.ownerName = ownerName;
			this.speed = speed; this.strength = strength; this.endurance = endurance;
		}
	}

	private static final class AgentData {
		private String name;
		private String uuid;
		private String dimension;
		private double x;
		private double y;
		private double z;
		private String mode;
		private String target;
		private AgentData(String name, String uuid, String dimension, double x, double y, double z, String mode, String target) {
			this.name = name; this.uuid = uuid; this.dimension = dimension;
			this.x = x; this.y = y; this.z = z; this.mode = mode; this.target = target;
		}
	}

	private static final class Decision {
		private String action;
		private String say;
		private double dx;
		private double dz;
		private Decision(String action, String say, double dx, double dz) {
			this.action = action; this.say = say; this.dx = dx; this.dz = dz;
		}
		private Decision safe() {
			String selected = action == null ? "wait" : action.toLowerCase(Locale.ROOT);
			if (!selected.equals("say") && !selected.equals("move") && !selected.equals("wait")) selected = "wait";
			String message = say == null ? "" : say.strip();
			if (message.length() > 240) message = message.substring(0, 240);
			return new Decision(selected, message, clamp(dx), clamp(dz));
		}
		private static double clamp(double value) { return Math.max(-8.0, Math.min(8.0, Double.isFinite(value) ? value : 0)); }
	}
}
