package com.celestial_manta.betterlapras

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object BetterLaprasEntities {
	val WATER_PULSE: EntityType<WaterPulseProjectile> = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		ResourceLocation.fromNamespaceAndPath("betterlapras", "water_pulse"),
		EntityType.Builder.of(::WaterPulseProjectile, MobCategory.MISC)
			.sized(1.0f, 1.0f)
			.clientTrackingRange(64)
			.updateInterval(1)
			.build(ResourceLocation.fromNamespaceAndPath("betterlapras", "water_pulse").toString()),
	)

	fun register() {
		WATER_PULSE.toString()
	}
}
