package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

/**
 * Ring size / height for Gigantamax Lapras cosmetics: beam force-field ring ([GmaxLaprasParticles])
 * and legacy end-rod helix ([GmaxLaprasEndRodWave]) if re-enabled.
 * Wave amplitude / k / Perish phase use [com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongConfig].
 */
object GmaxLaprasParticleConfig {
	const val RING_RADIUS_INNER = 9.0
	const val RING_RADIUS_OUTER = 9.0
	/** Vertical offset from entity feet so the ring sits around the body, not under the floor. */
	const val RING_Y_OFFSET = 5.5

	/**
	 * Gmax models are much larger than base Lapras; the force-field ring uses at least the end-rod radii
	 * and expands to sit outside the entity hitbox so it stays visible (same *relative* read as the helix).
	 */
	const val FORCE_FIELD_RING_BB_HORIZONTAL_MARGIN = 1.35
	const val FORCE_FIELD_RING_BB_VERTICAL_FRAC = 0.42

	/**
	 * Dark red cloud clusters: horizontal orbit radius as a fraction of the same
	 * [ring radius][GmaxLaprasParticles] uses (smaller = tighter above the body).
	 */
	const val CLOUD_ORBIT_RADIUS_FACTOR = 0.44

	/** Minimum feet-relative height for the center of the cloud masses. */
	const val CLOUD_Y_OFFSET = 13.8

	/**
	 * Feet-relative height uses the larger of [CLOUD_Y_OFFSET] and this fraction of
	 * entity bounding-box height so Gmax-sized Lapras keeps clouds clearly above the shell.
	 */
	const val CLOUD_BB_VERTICAL_FRAC = 0.88

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
