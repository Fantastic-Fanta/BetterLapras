package com.celestial_manta.betterlapras

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.min
import kotlin.math.tan

/** Geometry for Sheer Cold’s impact cone (apex at shot origin, opening toward the aimed point). */
object LaprasSheerColdCone {
	const val HALF_ANGLE_DEG: Double = 30.0

	private const val LENGTH_EXTEND_BLOCKS: Double = 4.0
	private const val AABB_PAD: Double = 1.5

	fun axisMaxDistance(apex: Vec3, targetPoint: Vec3): Double =
		apex.distanceTo(targetPoint) + LENGTH_EXTEND_BLOCKS

	fun entitySamplePoint(entity: LivingEntity): Vec3 {
		val y = entity.y + entity.bbHeight * 0.5
		return Vec3(entity.x, y, entity.z)
	}

	fun isInCone(apex: Vec3, targetPoint: Vec3, sample: Vec3): Boolean {
		val axis = targetPoint.subtract(apex)
		val axisLen = axis.length()
		if (axisLen < 1.0e-4) return apex.distanceTo(sample) <= 0.5
		val axisN = axis.scale(1.0 / axisLen)
		val maxDist = axisLen + LENGTH_EXTEND_BLOCKS
		val toSample = sample.subtract(apex)
		val t = toSample.dot(axisN)
		if (t < 0 || t > maxDist) return false
		val parallel = axisN.scale(t)
		val perp = toSample.subtract(parallel)
		val halfAngleRad = Math.toRadians(HALF_ANGLE_DEG)
		val maxPerp = t * tan(halfAngleRad)
		return perp.length() <= maxPerp + 0.15
	}

	fun queryRoughAabb(apex: Vec3, targetPoint: Vec3): AABB {
		val maxDist = axisMaxDistance(apex, targetPoint)
		val halfAngleRad = Math.toRadians(HALF_ANGLE_DEG)
		val lateral = maxDist * tan(halfAngleRad) + AABB_PAD
		val minX = min(apex.x, targetPoint.x) - lateral
		val maxX = maxOf(apex.x, targetPoint.x) + lateral
		val minY = min(apex.y, targetPoint.y) - lateral - AABB_PAD
		val maxY = maxOf(apex.y, targetPoint.y) + lateral + AABB_PAD
		val minZ = min(apex.z, targetPoint.z) - lateral
		val maxZ = maxOf(apex.z, targetPoint.z) + lateral
		return AABB(minX, minY, minZ, maxX, maxY, maxZ)
	}
}
