package com.celestial_manta.betterlapras.lapras.calls.perishsong

import com.celestial_manta.betterlapras.BetterLaprasParticles
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object PerishSongParticles {
	fun tickSessionParticles(level: ServerLevel, session: PerishSongSession, gameTime: Double, serverTick: Int) {
		val c = session.center
		val phaseRod = gameTime * PerishSongConfig.PERISH_WAVE_PHASE_PER_TICK
		val nRod = PerishSongConfig.PERISH_RING_SEGMENTS
		val rodRings = arrayOf(
			Pair(PerishSongConfig.PERISH_RING_RADIUS_INNER, 1.0),
			Pair(PerishSongConfig.PERISH_RING_RADIUS_OUTER, -1.0),
		)
		for (i in 0 until nRod) {
			val theta = (i / nRod.toDouble()) * 2.0 * PI
			for ((radius, phaseSign) in rodRings) {
				val x = c.x + radius * cos(theta)
				val z = c.z + radius * sin(theta)
				val y = c.y + PerishSongConfig.PERISH_RING_Y_OFFSET +
					PerishSongConfig.PERISH_WAVE_AMP_BLOCKS * sin(
						PerishSongConfig.PERISH_WAVE_PEAKS_AROUND_RING * theta + phaseSign * phaseRod,
					)
				sendPerishSongParticleForce(level, x, y, z)
			}
		}
		tickOccasionalNotes(level, c, serverTick, session.startTick)
	}

	private fun tickOccasionalNotes(
		level: ServerLevel,
		center: Vec3,
		tick: Int,
		sessionStartTick: Int,
	) {
		val elapsed = tick - sessionStartTick
		if (elapsed % PerishSongConfig.NOTE_SPAWN_INTERVAL_TICKS != 0) return
		val r = level.random
		val d = PerishSongConfig.NOTE_SPAWN_RADIUS_MAX - PerishSongConfig.NOTE_SPAWN_RADIUS_MIN
		repeat(PerishSongConfig.NOTE_SPAWN_COUNT) {
			val theta = r.nextDouble() * 2.0 * PI
			val radius = PerishSongConfig.NOTE_SPAWN_RADIUS_MIN + r.nextDouble() * d
			val x = center.x + radius * cos(theta)
			val z = center.z + radius * sin(theta)
			val y = center.y + PerishSongConfig.NOTE_SPAWN_Y_BASE + (r.nextDouble() - 0.5) * PerishSongConfig.NOTE_SPAWN_Y_JITTER
			sendFloatyNoteParticleForce(level, x, y, z, r.nextDouble())
		}
	}

	private fun sendPerishSongParticleForce(level: ServerLevel, x: Double, y: Double, z: Double) {
		val particle = BetterLaprasParticles.PERISH_SONG_END_ROD
		for (player in level.players()) {
			level.sendParticles(player, particle, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
		}
	}

	/** First velocity component is note pitch (0–1) for color on the client. */
	private fun sendFloatyNoteParticleForce(level: ServerLevel, x: Double, y: Double, z: Double, pitch: Double) {
		val particle = BetterLaprasParticles.FLOATY_NOTE
		for (player in level.players()) {
			level.sendParticles(player, particle, true, x, y, z, 1, pitch, 0.0, 0.0, 0.0)
		}
	}

	fun spawnEndRodBurst(level: ServerLevel, pos: Vec3) {
		val r = level.random
		repeat(PerishSongConfig.DEATH_BURST_PARTICLES) {
			val ox = (r.nextDouble() - 0.5) * 2.0 * PerishSongConfig.DEATH_BURST_SPREAD
			val oy = (r.nextDouble() - 0.5) * 2.0 * PerishSongConfig.DEATH_BURST_SPREAD
			val oz = (r.nextDouble() - 0.5) * 2.0 * PerishSongConfig.DEATH_BURST_SPREAD
			val particle = BetterLaprasParticles.PERISH_SONG_END_ROD
			val px = pos.x + ox
			val py = pos.y + oy + 0.4
			val pz = pos.z + oz
			for (player in level.players()) {
				level.sendParticles(player, particle, true, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
			}
		}
	}
}
