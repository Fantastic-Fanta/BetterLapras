package com.celestial_manta.betterlapras

import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasMoveProjectile
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object BetterLaprasEntities {
	private val LAPRAS_MOVE_PROJECTILE_ID: ResourceLocation =
		ResourceLocation.fromNamespaceAndPath("betterlapras", "lapras_move_projectile")

	val LAPRAS_MOVE_PROJECTILE: EntityType<LaprasMoveProjectile> = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		LAPRAS_MOVE_PROJECTILE_ID,
		EntityType.Builder.of(::LaprasMoveProjectile, MobCategory.MISC)
			.sized(1.0f, 1.0f)
			.clientTrackingRange(64)
			.updateInterval(1)
			.build(LAPRAS_MOVE_PROJECTILE_ID.toString()),
	)

	fun register() {
		LAPRAS_MOVE_PROJECTILE.toString()
	}
}
