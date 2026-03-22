package com.celestial_manta.betterlapras

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import org.slf4j.LoggerFactory

object BetterLaprasClient : ClientModInitializer {
	private val logger = LoggerFactory.getLogger("betterlapras")

	override fun onInitializeClient() {
		EntityRendererRegistry.register(BetterLaprasEntities.LAPRAS_MOVE_PROJECTILE, ::LaprasMoveProjectileRenderer)
		logger.info("BetterLapras client initialized")
	}
}
