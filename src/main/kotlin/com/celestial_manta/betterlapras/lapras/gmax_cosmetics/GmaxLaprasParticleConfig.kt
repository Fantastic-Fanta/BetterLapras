package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

/**
 * Ring size / height base for Gigantamax Lapras vanilla end rods ([GmaxLaprasEndRodWave]).
 * Wave amplitude / k / Perish phase use [com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongConfig].
 */
object GmaxLaprasParticleConfig {
	const val RING_RADIUS_INNER = 9.0
	const val RING_RADIUS_OUTER = 13.5
	/** Vertical offset from entity feet so the ring sits around the body, not under the floor. */
	const val RING_Y_OFFSET = 5.5

	/**
	 * Orbit rate: polar angle θ advances as `gameTime * this` (radians per game tick).
	 * Particles follow that θ so the sine wave is **drawn along the path** (trail), not a standing ring.
	 */
	const val ORBIT_RADIANS_PER_GAME_TICK = 0.05

	/**
	 * Emitters evenly spaced in phase (1 = one spiral/stream; 2+ = parallel streams around Lapras).
	 */
	const val ORBIT_EMITTER_COUNT = 20
}
