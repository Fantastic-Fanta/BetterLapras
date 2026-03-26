package com.celestial_manta.betterlapras

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object BetterLapras : ModInitializer {
	private val logger = LoggerFactory.getLogger("betterlapras")

	override fun onInitialize() {
		BetterLaprasSounds.register()
		BetterLaprasParticles.register()
		BetterLaprasEntities.register()
		PerishSongHandler.register()

		logger.info("BetterLapras loaded")
	}
}
