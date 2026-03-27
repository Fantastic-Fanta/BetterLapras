package com.celestial_manta.betterlapras.lapras.calls.perishsong

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.celestial_manta.betterlapras.network.LaprasPosableAnimationPackets
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

fun interface PerishSongMilestoneStep {
	fun run(level: ServerLevel, session: PerishSongSession)
}

object PerishSongMilestones {
	val STEPS: List<PerishSongMilestoneStep> = listOf(
		PerishSongMilestoneStep { level, session -> darkPulseAndCry(level, session) },
		PerishSongMilestoneStep { level, session -> darkPulseAndCry(level, session) },
		PerishSongMilestoneStep { level, session -> darkPulseAndCry(level, session) },
		PerishSongMilestoneStep { level, session -> resolve(session) },
	)

	private fun darkPulseAndCry(level: ServerLevel, session: PerishSongSession) {
		applyDarknessToMarked(level, session)
		broadcastFocalLaprasCry(level, session)
	}

	private fun broadcastFocalLaprasCry(level: ServerLevel, session: PerishSongSession) {
		val lapras = level.getEntity(session.focalLaprasUuid) as? PokemonEntity ?: return
		LaprasPosableAnimationPackets.broadcastLaprasCry(lapras)
	}

	/** One of three darkness pulses at 0s / 10s / 20s for targets still marked and in range. */
	private fun applyDarknessToMarked(level: ServerLevel, session: PerishSongSession) {
		for (uuid in session.marked.toList()) {
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			if (entity.position().distanceToSqr(session.center) > PerishSongConfig.markRadiusSq) continue
			entity.addEffect(
				MobEffectInstance(
					MobEffects.DARKNESS,
					PerishSongConfig.DARKNESS_WARNING_TICKS,
					0,
					false,
					false,
					true,
				),
			)
		}
	}

	private fun resolve(session: PerishSongSession) {
		val level = session.level
		for (uuid in session.marked.toList()) {
			if (uuid == session.focalLaprasUuid) continue
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			entity.kill()
		}
	}
}
