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

	// --- Gmax cloud cluster particle ([GmaxCloudClusterParticle]) ---

	const val CLOUD_CLUSTER_PARTICLE_LIFETIME_TICKS = 6000

	const val CLOUD_CLUSTER_PUFFS_PER_CLUSTER = 52
	const val CLOUD_CLUSTER_SPREAD = 0.82f
	const val CLOUD_CLUSTER_PUFF_QUAD_SIZE = 0.55f
	const val CLOUD_CLUSTER_ORBIT_RAD_PER_TICK = 0.04f
	const val CLOUD_CLUSTER_VERTICAL_BOB_AMP = 0.5f
	const val CLOUD_CLUSTER_BACK_OFFSET_BLOCKS = 2.0f
	const val CLOUD_CLUSTER_RUMBLE_AMP = 0.2f
	const val CLOUD_CLUSTER_RUMBLE_AMP_Y = 0.14f
	const val CLOUD_CLUSTER_QUAD_PULSE = 0.07f

	const val CLOUD_CLUSTER_TINT_R = 0.52f
	const val CLOUD_CLUSTER_TINT_G = 0.07f
	const val CLOUD_CLUSTER_TINT_B = 0.09f
	const val CLOUD_CLUSTER_TINT_A = 0.94f

	const val CLOUD_CLUSTER_PUFF_SCALE_MIN = 0.72f
	const val CLOUD_CLUSTER_PUFF_SCALE_SPREAD = 0.45f
	const val CLOUD_CLUSTER_PUFF_ALPHA_MIN = 0.68f
	const val CLOUD_CLUSTER_PUFF_ALPHA_SPREAD = 0.28f
	const val CLOUD_CLUSTER_RUMBLE_SPEED_MIN = 0.052f
	const val CLOUD_CLUSTER_RUMBLE_SPEED_SPREAD = 0.095f

	/** Main cluster billboard alpha (multi-puff aggregate). */
	const val CLOUD_CLUSTER_MAIN_ALPHA = 0.85f
}
