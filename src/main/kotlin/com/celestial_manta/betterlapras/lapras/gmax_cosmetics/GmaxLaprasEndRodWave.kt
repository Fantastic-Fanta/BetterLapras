package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

import com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongConfig
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vanilla [ParticleTypes.END_ROD] **orbiting** emitters: θ advances with time, height follows
 * `sin(k·θ + phaseSign·ω·t)` like Perish Song, but θ is **orbit angle** (particles move around Lapras),
 * so the trig wave appears as a **trail** along a helix — not a full ring of samples redrawn each tick
 * (which looks like a standing / breathing wave).
 *
 * Velocity matches the parametric derivative so end rods drift along the curve instead of only popping in place.
 */
object GmaxLaprasEndRodWave {
	fun tick(level: ServerLevel, center: Vec3, gameTime: Double) {
		val orbitOmega = GmaxLaprasParticleConfig.ORBIT_RADIANS_PER_GAME_TICK
		val thetaBase = gameTime * orbitOmega
		val phaseRod = gameTime * PerishSongConfig.PERISH_WAVE_PHASE_PER_TICK
		val k = PerishSongConfig.PERISH_WAVE_PEAKS_AROUND_RING
		val amp = PerishSongConfig.PERISH_WAVE_AMP_BLOCKS
		val omegaPhase = PerishSongConfig.PERISH_WAVE_PHASE_PER_TICK
		val baseY = center.y + GmaxLaprasParticleConfig.RING_Y_OFFSET
		val emitters = GmaxLaprasParticleConfig.ORBIT_EMITTER_COUNT.coerceAtLeast(1)
		val rodRings = arrayOf(
			Pair(GmaxLaprasParticleConfig.RING_RADIUS_INNER, 1.0),
			Pair(GmaxLaprasParticleConfig.RING_RADIUS_OUTER, -1.0),
		)
		for (j in 0 until emitters) {
			val theta = thetaBase + (j / emitters.toDouble()) * 2.0 * PI
			val sinT = sin(theta)
			val cosT = cos(theta)
			for ((radius, phaseSign) in rodRings) {
				val x = center.x + radius * cosT
				val z = center.z + radius * sinT
				val wavePhase = k * theta + phaseSign * phaseRod
				val y = baseY + amp * sin(wavePhase)
				val dWave = k * orbitOmega + phaseSign * omegaPhase
				val vy = amp * cos(wavePhase) * dWave
				val vx = -radius * sinT * orbitOmega
				val vz = radius * cosT * orbitOmega
				sendVanillaEndRod(level, x, y, z, vx, vy, vz)
			}
		}
	}

	private fun sendVanillaEndRod(
		level: ServerLevel,
		x: Double,
		y: Double,
		z: Double,
		vx: Double,
		vy: Double,
		vz: Double,
	) {
		for (player in level.players()) {
			level.sendParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, vx, vy, vz, 0.0)
		}
	}
}
