package com.celestial_manta.betterlapras

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.resources.ResourceLocation

object BetterLaprasParticles {
	val PERISH_SONG_END_ROD_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "perish_song_end_rod")

	val PERISH_SONG_END_ROD: SimpleParticleType = FabricParticleTypes.simple()

	val FLOATY_NOTE_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "floaty_note")

	val FLOATY_NOTE: SimpleParticleType = FabricParticleTypes.simple()

	val GMAX_FORCE_FIELD_RING_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "gmax_force_field_ring")
		
	val GMAX_FORCE_FIELD_RING: SimpleParticleType = FabricParticleTypes.simple(true)

	val GMAX_CLOUD_CLUSTERS_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "gmax_cloud_clusters")

	val GMAX_CLOUD_CLUSTERS: SimpleParticleType = FabricParticleTypes.simple(true)

	fun register() {
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, PERISH_SONG_END_ROD_ID, PERISH_SONG_END_ROD)
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, FLOATY_NOTE_ID, FLOATY_NOTE)
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, GMAX_FORCE_FIELD_RING_ID, GMAX_FORCE_FIELD_RING)
		net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, GMAX_CLOUD_CLUSTERS_ID, GMAX_CLOUD_CLUSTERS)
	}
}
