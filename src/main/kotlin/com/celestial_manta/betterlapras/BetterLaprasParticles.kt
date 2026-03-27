package com.celestial_manta.betterlapras

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.resources.ResourceLocation

/**
 * Custom [SimpleParticleType]s: end rod (short life), floaty note (vanilla note look, gentle drift).
 */
object BetterLaprasParticles {
	val PERISH_SONG_END_ROD_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "perish_song_end_rod")

	val PERISH_SONG_END_ROD: SimpleParticleType = FabricParticleTypes.simple()

	val FLOATY_NOTE_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "floaty_note")

	val FLOATY_NOTE: SimpleParticleType = FabricParticleTypes.simple()

	fun register() {
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, PERISH_SONG_END_ROD_ID, PERISH_SONG_END_ROD)
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, FLOATY_NOTE_ID, FLOATY_NOTE)
	}
}
