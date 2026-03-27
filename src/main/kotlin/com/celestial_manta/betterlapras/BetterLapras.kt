package com.celestial_manta.betterlapras

import com.celestial_manta.betterlapras.commands.CobbreedingEggCommands
import com.celestial_manta.betterlapras.content.blocks.BlocksRegistration
import com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object BetterLapras : ModInitializer {
	private val logger = LoggerFactory.getLogger("betterlapras")

	override fun onInitialize() {
		BetterLaprasSounds.register()
		BetterLaprasParticles.register()
		BetterLaprasEntities.register()
		BlocksRegistration.register()
		PerishSongHandler.register()

		if (FabricLoader.getInstance().isModLoaded("cobbreeding")) {
			CobbreedingEggCommands.register()
			logger.info("Cobbreeding detected: registered /lapras egg commands")
		}

		logger.info("BetterLapras loaded")
	}
}
