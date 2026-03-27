package com.celestial_manta.betterlapras.lapras.moves.projectile

/**
 * Per-[LaprasPulseKind] strike damage for ranged volleys and melee-replaced shots.
 * Edit the private functions below to tune each move profile independently.
 */
object LaprasMoveDamageFormulas {

	fun compute(kind: LaprasPulseKind, pokemonLevel: Int): Double {
		val level = pokemonLevel.coerceIn(1, 100)
		return when (kind) {
			LaprasPulseKind.ICE_BEAM_MOVE -> iceBeam(level)
			LaprasPulseKind.SHEER_COLD -> sheerCold(level)
			LaprasPulseKind.ICE_SHARD -> iceShard(level)
			LaprasPulseKind.HYDRO_PUMP -> hydroPump(level)
			LaprasPulseKind.BUBBLE_BEAM_WATER -> bubbleBeamWater(level)
			LaprasPulseKind.WATER_GUN_OFFTYPE -> waterGunOfftype(level)
		}
	}

	// --- Edit formulas below (input: Cobblemon Pokémon level, typically 1–100) ---

	private fun iceBeam(level: Int): Double =
		(2.0 + level * 0.35).coerceIn(2.0, 14.0)

	private fun sheerCold(level: Int): Double =
		(10.0 + level * 0.75).coerceIn(10.0, 70.0) * 0.5

	private fun iceShard(level: Int): Double =
		(2.0 + level * 0.28).coerceIn(1.0, 12.0) * 0.25

	private fun hydroPump(level: Int): Double =
		(3.2 + level * 0.32).coerceIn(3.2, 23.0)

	private fun bubbleBeamWater(level: Int): Double =
		(0.85 + level * 0.22).coerceIn(0.85, 8.5)

	private fun waterGunOfftype(level: Int): Double =
		(0.7 + level * 0.18).coerceIn(0.7, 6.5)
}
