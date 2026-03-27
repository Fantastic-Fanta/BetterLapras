package com.celestial_manta.betterlapras.lapras.moves.projectile

import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * Invisible logical projectile — Cobblemon Snowstorm packets from the server provide all visible VFX.
 */
class LaprasMoveProjectileRenderer(ctx: EntityRendererProvider.Context) : EntityRenderer<LaprasMoveProjectile>(ctx) {

	override fun getTextureLocation(entity: LaprasMoveProjectile): ResourceLocation =
		ResourceLocation.withDefaultNamespace("textures/misc/white.png")

	override fun shouldRender(
		entity: LaprasMoveProjectile,
		frustum: Frustum,
		camX: Double,
		camY: Double,
		camZ: Double,
	): Boolean = false
}
