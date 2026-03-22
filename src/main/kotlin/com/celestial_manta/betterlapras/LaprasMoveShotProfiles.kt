package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation

/**
 * Cobblemon Snowstorm effect ids + move sound paths for one projectile visual style.
 * Paths are Cobblemon sound registry ids without namespace (passed to [ResourceLocation.fromNamespaceAndPath]("cobblemon", path)).
 */
data class PulsePresentation(
	val trailActor: ResourceLocation,
	val trailSuds: ResourceLocation,
	val trailRing: ResourceLocation,
	val targetEffect: ResourceLocation,
	val targetSplash: ResourceLocation,
	val blockSplash: ResourceLocation,
	val actorSoundPath: String,
	val targetSoundPathPrimary: String,
	val targetSoundPathSecondary: String?,
)

enum class LaprasPulseKind {
	WATER_DEFAULT,
	HYDRO_PUMP,
	ICE_BEAM,
	/** Non-water slot-1 move: Water Pulse Snowstorm only, no vanilla splash trail; slower / weaker. */
	OFF_TYPE_WATER,
	;

	companion object {
		fun fromOrdinal(ordinal: Int): LaprasPulseKind =
			entries.getOrElse(ordinal.coerceIn(0, entries.size - 1)) { WATER_DEFAULT }
	}
}

/**
 * Resolved from Lapras move slot 0: null means BetterLapras does not fire ranged pulses.
 */
data class LaprasShotProfile(
	val kind: LaprasPulseKind,
	val presentation: PulsePresentation,
	val projectileSpeed: Float,
	val inaccuracy: Float,
	/** Multiplier on [LaprasProjectileCombat] base pulse damage (1.0 = current water default). */
	val damageMultiplier: Double,
	/**
	 * Extra ticks after travel time before scheduled impact; see [WaterPulseProjectile.computeImpactDelayTicks].
	 * Hydro uses a smaller pad so a fast bolt still lines up with hit VFX.
	 */
	val impactPadTicks: Int = 18,
)

object LaprasMoveShotProfiles {
	private val HYDRO_PUMP_NAME = "hydropump"

	private val WATER_PULSE_PRESENTATION = PulsePresentation(
		trailActor = ResourceLocation.parse("cobblemon:waterpulse_actor"),
		trailSuds = ResourceLocation.parse("cobblemon:waterpulse_actorsuds"),
		trailRing = ResourceLocation.parse("cobblemon:waterpulse_actorring"),
		targetEffect = ResourceLocation.parse("cobblemon:waterpulse_target"),
		targetSplash = ResourceLocation.parse("cobblemon:waterpulse_targetsplash"),
		blockSplash = ResourceLocation.parse("cobblemon:waterpulse_targetsplash"),
		actorSoundPath = "move.waterpulse.actor",
		targetSoundPathPrimary = "move.waterpulse.target",
		targetSoundPathSecondary = null,
	)

	private val ICE_BEAM_PRESENTATION = PulsePresentation(
		trailActor = ResourceLocation.parse("cobblemon:icebeam_actorpilot"),
		trailSuds = ResourceLocation.parse("cobblemon:icebeam_actorspray"),
		trailRing = ResourceLocation.parse("cobblemon:icebeam_actorfrost"),
		targetEffect = ResourceLocation.parse("cobblemon:icebeam_target"),
		targetSplash = ResourceLocation.parse("cobblemon:icebeam_targetburst"),
		blockSplash = ResourceLocation.parse("cobblemon:icebeam_targetburst"),
		actorSoundPath = "move.icebeam.actor",
		targetSoundPathPrimary = "move.icebeam.target_1",
		targetSoundPathSecondary = "move.icebeam.target_2",
	)

	private const val DEFAULT_PROJECTILE_SPEED = 7.25f
	private const val DEFAULT_INACCURACY = 3.5f
	/** Tight, fast bolt; still uses Water Pulse Snowstorm ids. */
	private const val HYDRO_PROJECTILE_SPEED = 16f
	private const val HYDRO_INACCURACY = 0f
	private const val HYDRO_DAMAGE_MULT = 1.65
	private const val HYDRO_IMPACT_PAD_TICKS = 9

	/** Slot 1 is not Water / Hydro / Ice: sluggish generic Water Pulse bolt. */
	private const val OFF_TYPE_PROJECTILE_SPEED = 5f
	private const val OFF_TYPE_INACCURACY = 2.5f
	private const val OFF_TYPE_DAMAGE_MULT = 0.5

	fun presentationForKind(kind: LaprasPulseKind): PulsePresentation = when (kind) {
		LaprasPulseKind.ICE_BEAM -> ICE_BEAM_PRESENTATION
		LaprasPulseKind.WATER_DEFAULT, LaprasPulseKind.HYDRO_PUMP, LaprasPulseKind.OFF_TYPE_WATER ->
			WATER_PULSE_PRESENTATION
	}

	/**
	 * Slot 1 empty → null (no ranged). Hydro Pump by template name (before type checks).
	 * Ice type → Ice Beam VFX. Water type → normal water tuning. Anything else → slow weak Cobblemon-only pulse (no vanilla trail).
	 */
	fun resolveShotProfile(pokemon: PokemonEntity): LaprasShotProfile? {
		val move = pokemon.pokemon.moveSet.getMovesWithNulls().getOrNull(0) ?: return null

		if (move.template.name.equals(HYDRO_PUMP_NAME, ignoreCase = true)) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.HYDRO_PUMP,
				presentation = WATER_PULSE_PRESENTATION,
				projectileSpeed = HYDRO_PROJECTILE_SPEED,
				inaccuracy = HYDRO_INACCURACY,
				damageMultiplier = HYDRO_DAMAGE_MULT,
				impactPadTicks = HYDRO_IMPACT_PAD_TICKS,
			)
		}

		if (move.type == ElementalTypes.ICE) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.ICE_BEAM,
				presentation = ICE_BEAM_PRESENTATION,
				projectileSpeed = DEFAULT_PROJECTILE_SPEED,
				inaccuracy = DEFAULT_INACCURACY,
				damageMultiplier = 1.0,
			)
		}

		if (move.type == ElementalTypes.WATER) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.WATER_DEFAULT,
				presentation = WATER_PULSE_PRESENTATION,
				projectileSpeed = DEFAULT_PROJECTILE_SPEED,
				inaccuracy = DEFAULT_INACCURACY,
				damageMultiplier = 1.0,
			)
		}

		return LaprasShotProfile(
			kind = LaprasPulseKind.OFF_TYPE_WATER,
			presentation = WATER_PULSE_PRESENTATION,
			projectileSpeed = OFF_TYPE_PROJECTILE_SPEED,
			inaccuracy = OFF_TYPE_INACCURACY,
			damageMultiplier = OFF_TYPE_DAMAGE_MULT,
		)
	}
}
