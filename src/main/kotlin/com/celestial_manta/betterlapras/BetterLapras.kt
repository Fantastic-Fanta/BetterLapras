package com.celestial_manta.betterlapras

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object BetterLapras : ModInitializer {
	private val logger = LoggerFactory.getLogger("betterlapras")

	override fun onInitialize() {
		BetterLaprasEntities.register()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			dispatcher.register(
				Commands.literal("betterlapras").executes { ctx ->
					ctx.source.sendSuccess(
						{
							Component.literal(
								"BetterLapras: owned Lapras in the Overworld fires Cobblemon Water Pulse–style pulses at hostile targets from about "
									+ LaprasProjectileCombat.RANGED_MIN_DISTANCE.toInt() + "–"
									+ LaprasProjectileCombat.RANGED_MAX_DISTANCE.toInt()
									+ " blocks (plus point-blank when Cobblemon tries to melee).",
							)
						},
						false,
					)
					1
				},
			)
		}

		logger.info("BetterLapras loaded")
	}
}
