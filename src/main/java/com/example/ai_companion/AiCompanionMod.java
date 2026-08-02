package com.example.ai_companion;

import com.example.ai_companion.agent.AgentManager;
import com.example.ai_companion.command.AiPlayerCommands;
import com.example.ai_companion.config.GameplayConfig;
import com.example.ai_companion.config.ModConfig;
import com.example.ai_companion.config.PromptStore;
import com.example.ai_companion.gameplay.GoldenSpearRush;
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

	@Override
	public void onInitialize() {
		config = ModConfig.load();
		gameplay = GameplayConfig.load();
		prompts = PromptStore.loadServer();
		agents = new AgentManager(() -> config, prompts);
		goldenSpearRush = new GoldenSpearRush(() -> gameplay);
		goldenSpearRush.register();
		AiPlayerCommands.register(agents, prompts, () -> config, updated -> config = updated,
			() -> gameplay, updated -> gameplay = updated);
		ServerTickEvents.END_SERVER_TICK.register(agents::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			agents.close();
			goldenSpearRush.clearCounters();
		});
		LOGGER.info("WindowsdePC's AI Companion Mod initialized. API key present: {}", config.hasApiKey());
	}
}
