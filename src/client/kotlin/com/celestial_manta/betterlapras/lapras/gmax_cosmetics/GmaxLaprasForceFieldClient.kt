package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.Entity

/**
 * Client-side checks for the Gmax force-field ring (Java particle calls into this).
 */
object GmaxLaprasForceFieldClient {
	@JvmStatic
	fun shouldKeepForceFieldRingFor(entity: Entity): Boolean {
		if (entity !is PokemonEntity) return false
		if (entity.pokemon.species.resourceIdentifier.path != "lapras") return false
		return "gmax" in entity.pokemon.aspects
	}
}
