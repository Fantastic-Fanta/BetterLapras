package com.celestial_manta.betterlapras

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.resources.ResourceLocation

/**
 * Custom [SimpleParticleType] rendered like the end rod particle; client uses a shorter max age
 * (see [com.celestial_manta.betterlapras.PerishSongEndRodParticle]).
 */
object BetterLaprasParticles {
	val PERISH_SONG_END_ROD_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "perish_song_end_rod")

	val PERISH_SONG_END_ROD: SimpleParticleType = FabricParticleTypes.simple()

	fun register() {
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, PERISH_SONG_END_ROD_ID, PERISH_SONG_END_ROD)
	}
}
