package com.example.ai_companion;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.command.AiBattleCommands;
import com.example.ai_companion.command.AiPlayerCommands;
import com.example.ai_companion.command.AssistantOrbCommands;
import com.example.ai_companion.command.PhotographyCommands;
import com.example.ai_companion.command.ExplorerNavigationCommands;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.GoldenSpearRush;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import com.example.ai_companion.gameplay.MinigameRewardManager;
import com.example.ai_companion.exploration.ExplorerNavigationItems;
import com.example.ai_companion.exploration.ExplorerNavigationManager;
import com.example.ai_companion.exploration.MercifulVoidController;
import com.example.ai_companion.exploration.WorldLimitController;
import com.example.ai_companion.network.AgentPositionNetworking;
import com.example.ai_companion.network.NavigationHudPayload;
import com.example.ai_companion.orb.AssistantOrbManager;
import com.example.ai_companion.photo.PhotographyItems;
import com.example.ai_companion.photo.PhotographyManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main Fabric entry point. */
public final class AiCompanionMod implements ModInitializer {
	public static final String MOD_ID = "ai_companion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ModConfig config;
	private AgentManager agents;
	private PromptStore prompts;
	private GameplayConfig gameplay;
	private GoldenSpearRush goldenSpearRush;
	private MinigameRewardManager minigameRewards;
	private AiArenaManager arena;
	private AssistantOrbManager assistantOrb;
	private PhotographyManager photography;
	private ExplorerNavigationManager explorerNavigation;
	private WorldLimitController worldLimits;
	private MercifulVoidController mercifulVoid;

	@Override
	public void onInitialize() {
		config = ModConfig.load();
		gameplay = GameplayConfig.load();
		FlexibleEquipmentMode.configureServer(() -> gameplay.flexibleEquipmentEnabled());
		prompts = PromptStore.loadServer();
		agents = new AgentManager(() -> config, prompts);
		arena = new AiArenaManager(agents);
		assistantOrb = new AssistantOrbManager(() -> config);
		photography = new PhotographyManager(() -> config);
		explorerNavigation = new ExplorerNavigationManager(() -> gameplay);
		worldLimits = new WorldLimitController(() -> gameplay);
		mercifulVoid = new MercifulVoidController(() -> gameplay);
		AgentPositionNetworking.registerServer(agents);
		PayloadTypeRegistry.clientboundPlay().register(NavigationHudPayload.TYPE,
			NavigationHudPayload.CODEC);
		goldenSpearRush = new GoldenSpearRush(() -> gameplay);
		minigameRewards = new MinigameRewardManager();
		goldenSpearRush.register();
		AiPlayerCommands.register(agents, prompts, () -> config, updated -> config = updated,
			() -> gameplay, updated -> gameplay = updated, minigameRewards);
		AiBattleCommands.register(arena);
		AssistantOrbCommands.register(assistantOrb);
		PhotographyCommands.register(photography);
		PhotographyItems.register(photography);
		ExplorerNavigationItems.register(explorerNavigation);
		ExplorerNavigationCommands.register(explorerNavigation, () -> gameplay,
			updated -> gameplay = updated);
		ServerTickEvents.END_SERVER_TICK.register(agents::tick);
		ServerTickEvents.END_SERVER_TICK.register(arena::tick);
		ServerTickEvents.END_SERVER_TICK.register(assistantOrb::tick);
		ServerTickEvents.END_SERVER_TICK.register(photography::tick);
		ServerTickEvents.END_SERVER_TICK.register(explorerNavigation::tick);
		ServerTickEvents.END_SERVER_TICK.register(worldLimits::tick);
		ServerTickEvents.END_SERVER_TICK.register(mercifulVoid::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			arena.close();
			agents.close();
			goldenSpearRush.clearCounters();
			minigameRewards.clear();
			assistantOrb.close();
			photography.close();
			explorerNavigation.close();
			worldLimits.close();
			mercifulVoid.close();
		});
		LOGGER.info("WindowsdePC's AI Companion Mod initialized. API key present: {}", config.hasApiKey());
	}
}
