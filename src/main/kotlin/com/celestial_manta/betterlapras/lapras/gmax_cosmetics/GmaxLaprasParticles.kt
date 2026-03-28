package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

import com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongParticles
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import kotlin.jvm.JvmStatic

object GmaxLaprasParticles {
	@JvmStatic
	fun tick(entity: PokemonEntity) {
		if (entity.level().isClientSide) return
		val level = entity.level() as? ServerLevel ?: return
		if (entity.pokemon.species.resourceIdentifier.path != "lapras") return
		if ("gmax" !in entity.pokemon.aspects) return
		val t = level.gameTime.toDouble()
		PerishSongParticles.tickEndRodRingsAroundCenter(
			level,
			entity.position(),
			t,
			GmaxLaprasParticleConfig.RING_RADIUS_INNER,
			GmaxLaprasParticleConfig.RING_RADIUS_OUTER,
			GmaxLaprasParticleConfig.RING_Y_OFFSET,
		)
	}
}
