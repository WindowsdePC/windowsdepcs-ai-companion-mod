package com.example.ai_companion;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.command.AiBattleCommands;
import com.example.ai_companion.command.AiPlayerCommands;
import com.example.ai_companion.command.AssistantOrbCommands;
import com.example.ai_companion.command.CollaborationCommands;
import com.example.ai_companion.command.PhotographyCommands;
import com.example.ai_companion.command.TravelLogCommands;
import com.example.ai_companion.command.MinecraftDailyNewsCommands;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.cooperation.CollaborationManager;
import com.example.ai_companion.gameplay.GoldenSpearRush;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import com.example.ai_companion.gameplay.MinigameRewardManager;
import com.example.ai_companion.network.AgentPositionNetworking;
import com.example.ai_companion.orb.AssistantOrbManager;
import com.example.ai_companion.photo.PhotographyItems;
import com.example.ai_companion.photo.PhotographyManager;
import com.example.ai_companion.travel.TravelLogManager;
import com.example.ai_companion.news.MinecraftDailyNewsManager;
import com.example.ai_companion.navigation.NavigationNetworking;
import com.example.ai_companion.world.WorldFeatureCommands;
import com.example.ai_companion.world.WorldFeatureConfig;
import com.example.ai_companion.world.WorldFeatureManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
	private TravelLogManager travelLog;
	private MinecraftDailyNewsManager dailyNews;
	private CollaborationManager collaboration;
	private WorldFeatureConfig worldFeatures;
	private WorldFeatureManager worldFeatureManager;

	@Override
	public void onInitialize() {
		config = ModConfig.load();
		gameplay = GameplayConfig.load();
		FlexibleEquipmentMode.configureServer(() -> gameplay.flexibleEquipmentEnabled());
		prompts = PromptStore.loadServer();
		agents = new AgentManager(() -> config, prompts);
		collaboration = new CollaborationManager(agents);
		agents.setCollaborationContext(collaboration::promptContext);
		arena = new AiArenaManager(agents);
		assistantOrb = new AssistantOrbManager(() -> config);
		photography = new PhotographyManager(() -> config);
		travelLog = new TravelLogManager();
		dailyNews = new MinecraftDailyNewsManager(() -> config, agents, arena);
		worldFeatures = WorldFeatureConfig.load();
		worldFeatureManager = new WorldFeatureManager(() -> worldFeatures);
		AgentPositionNetworking.registerServer(agents);
		NavigationNetworking.registerServer(() -> worldFeatures);
		goldenSpearRush = new GoldenSpearRush(() -> gameplay);
		minigameRewards = new MinigameRewardManager();
		goldenSpearRush.register();
		AiPlayerCommands.register(agents, prompts, () -> config, updated -> config = updated,
			() -> gameplay, updated -> gameplay = updated, minigameRewards);
		AiBattleCommands.register(arena);
		CollaborationCommands.register(collaboration);
		AssistantOrbCommands.register(assistantOrb);
		PhotographyCommands.register(photography);
		PhotographyItems.register(photography);
		TravelLogCommands.register(travelLog, photography);
		MinecraftDailyNewsCommands.register(dailyNews);
		WorldFeatureCommands.register(() -> worldFeatures, updated -> worldFeatures = updated);
		ServerTickEvents.END_SERVER_TICK.register(agents::tick);
		ServerTickEvents.END_SERVER_TICK.register(arena::tick);
		ServerTickEvents.END_SERVER_TICK.register(assistantOrb::tick);
		ServerTickEvents.END_SERVER_TICK.register(photography::tick);
		ServerTickEvents.END_SERVER_TICK.register(travelLog::tick);
		ServerTickEvents.END_SERVER_TICK.register(dailyNews::tick);
		ServerTickEvents.END_SERVER_TICK.register(worldFeatureManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(agents::restore);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			arena.close();
			agents.close();
			collaboration.close();
			goldenSpearRush.clearCounters();
			minigameRewards.clear();
			assistantOrb.close();
			photography.close();
			travelLog.close();
			dailyNews.close();
			worldFeatureManager.close();
		});
		LOGGER.info("WindowsdePC's AI Companion Mod initialized. API key present: {}", config.hasApiKey());
	}
}
