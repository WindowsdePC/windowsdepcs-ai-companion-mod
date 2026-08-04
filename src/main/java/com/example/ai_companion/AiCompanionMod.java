package com.example.ai_companion;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.arena.AiArenaManager;
import com.example.ai_companion.command.AiBattleCommands;
import com.example.ai_companion.command.AiPlayerCommands;
import com.example.ai_companion.command.AssistantOrbCommands;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.GoldenSpearRush;
import com.example.ai_companion.gameplay.FlexibleEquipmentMode;
import com.example.ai_companion.gameplay.MinigameRewardManager;
import com.example.ai_companion.network.AgentPositionNetworking;
import com.example.ai_companion.orb.AssistantOrbManager;
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

	@Override
	public void onInitialize() {
		config = ModConfig.load();
		gameplay = GameplayConfig.load();
		FlexibleEquipmentMode.configureServer(() -> gameplay.flexibleEquipmentEnabled());
		prompts = PromptStore.loadServer();
		agents = new AgentManager(() -> config, prompts);
		arena = new AiArenaManager(agents);
		assistantOrb = new AssistantOrbManager(() -> config);
		AgentPositionNetworking.registerServer(agents);
		goldenSpearRush = new GoldenSpearRush(() -> gameplay);
		minigameRewards = new MinigameRewardManager();
		goldenSpearRush.register();
		AiPlayerCommands.register(agents, prompts, () -> config, updated -> config = updated,
			() -> gameplay, updated -> gameplay = updated, minigameRewards);
		AiBattleCommands.register(arena);
		AssistantOrbCommands.register(assistantOrb);
		ServerTickEvents.END_SERVER_TICK.register(agents::tick);
		ServerTickEvents.END_SERVER_TICK.register(arena::tick);
		ServerTickEvents.END_SERVER_TICK.register(assistantOrb::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			arena.close();
			agents.close();
			goldenSpearRush.clearCounters();
			minigameRewards.clear();
			assistantOrb.close();
		});
		LOGGER.info("WindowsdePC's AI Companion Mod initialized. API key present: {}", config.hasApiKey());
	}
}
