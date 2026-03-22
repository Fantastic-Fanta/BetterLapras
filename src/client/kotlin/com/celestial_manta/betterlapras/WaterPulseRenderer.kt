package com.celestial_manta.betterlapras

import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * No Minecraft item/model — the pulse is drawn only by Cobblemon Snowstorm packets spawned server-side
 * ([WaterPulseProjectile]) using the same effect ids as move Water Pulse.
 */
class WaterPulseRenderer(ctx: EntityRendererProvider.Context) : EntityRenderer<WaterPulseProjectile>(ctx) {

	override fun getTextureLocation(entity: WaterPulseProjectile): ResourceLocation =
		ResourceLocation.withDefaultNamespace("textures/misc/white.png")

	override fun shouldRender(
		entity: WaterPulseProjectile,
		frustum: Frustum,
		camX: Double,
		camY: Double,
		camZ: Double,
	): Boolean = false
}
