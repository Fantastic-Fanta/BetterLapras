package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation

private fun rl(path: String): ResourceLocation = ResourceLocation.parse(path)

/**
 * Persisted on projectiles / resolved from slot-1 move. Each maps to distinct VFX + range + damage formula.
 */
enum class LaprasPulseKind {
	ICE_BEAM_MOVE,
	SHEER_COLD,
	ICE_SHARD,
	HYDRO_PUMP,
	BUBBLE_BEAM_WATER,
	WATER_GUN_OFFTYPE,
	;

	companion object {
		const val NBT_KIND_ID = "BetterLaprasPulseKindId"
		const val NBT_KIND_LEGACY_ORDINAL = "BetterLaprasPulseKind"

		fun fromPersistentId(id: String): LaprasPulseKind =
			entries.find { it.name == id } ?: WATER_GUN_OFFTYPE

		/** Pre–multi-profile ordinals: 0 WATER_DEFAULT, 1 HYDRO, 2 ICE_BEAM, 3 OFF_TYPE. */
		fun fromLegacyOrdinal(ordinal: Int): LaprasPulseKind = when (ordinal) {
			0 -> BUBBLE_BEAM_WATER
			1 -> HYDRO_PUMP
			2 -> ICE_BEAM_MOVE
			3 -> WATER_GUN_OFFTYPE
			else -> WATER_GUN_OFFTYPE
		}
	}
}

data class PulsePresentation(
	val trailPulseEffect: ResourceLocation,
	val trailSecondaryPulseEffect: ResourceLocation? = null,
	val trailSourceLocators: List<String> = listOf("special", "target"),
	val trailTargetLocators: List<String> = listOf("target"),
	val targetEffect: ResourceLocation,
	val blockSplash: ResourceLocation,
	val actorSoundPath: String,
	val targetSoundPathPrimary: String,
	val targetSoundPathSecondary: String?,
)

data class LaprasShotProfile(
	val kind: LaprasPulseKind,
	val presentation: PulsePresentation,
	val rangedMinBlocks: Double,
	val rangedMaxBlocks: Double,
	val projectileSpeed: Float,
	val inaccuracy: Float,
	val impactPadTicks: Int = 18,
)

object LaprasMoveShotProfiles {
	private const val HYDRO_PUMP_NAME = "hydropump"
	private const val ICE_BEAM_NAME = "icebeam"
	private const val SHEER_COLD_NAME = "sheercold"

	private val ICE_BEAM_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:icebeam_actorpilot"),
		targetEffect = rl("cobblemon:icebeam_target"),
		blockSplash = rl("cobblemon:icebeam_targetburst"),
		actorSoundPath = "move.icebeam.actor",
		targetSoundPathPrimary = "move.icebeam.target_1",
		targetSoundPathSecondary = "move.icebeam.target_2",
	)

	private val SHEER_COLD_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:powdersnow_actor"),
		targetEffect = rl("cobblemon:powdersnow_target"),
		blockSplash = rl("cobblemon:powdersnow_target"),
		actorSoundPath = "move.powdersnow.actor",
		targetSoundPathPrimary = "move.powdersnow.target",
		targetSoundPathSecondary = null,
	)

	/** No dedicated iceshard move sounds in Cobblemon assets — swap paths if you add custom sounds. */
	private val ICE_SHARD_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:iceshard_actor"),
		trailSourceLocators = listOf("special", "middle"),
		trailTargetLocators = listOf("target"),
		targetEffect = rl("cobblemon:iceshard_target"),
		blockSplash = rl("cobblemon:iceshard_targetmist"),
		actorSoundPath = "move.icebeam.actor",
		targetSoundPathPrimary = "move.icebeam.target_1",
		targetSoundPathSecondary = null,
	)

	private val HYDRO_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:waterpulse_actor"),
		trailSecondaryPulseEffect = rl("cobblemon:waterpulse_actorring"),
		targetEffect = rl("cobblemon:waterpulse_target"),
		blockSplash = rl("cobblemon:waterpulse_targetsplash"),
		actorSoundPath = "move.waterpulse.actor",
		targetSoundPathPrimary = "move.waterpulse.target",
		targetSoundPathSecondary = null,
	)

	private val BUBBLE_BEAM_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:bubblebeam_actor"),
		targetEffect = rl("cobblemon:bubblebeam_pop"),
		blockSplash = rl("cobblemon:bubblebeam_pop"),
		actorSoundPath = "move.bubblebeam.actor",
		targetSoundPathPrimary = "move.bubblebeam.target",
		targetSoundPathSecondary = null,
	)

	private val WATER_GUN_PRESENTATION = PulsePresentation(
		trailPulseEffect = rl("cobblemon:watergun_actor"),
		targetEffect = rl("cobblemon:watergun_target"),
		blockSplash = rl("cobblemon:watergun_targetfoam"),
		actorSoundPath = "move.watergun.actor",
		targetSoundPathPrimary = "move.watergun.target",
		targetSoundPathSecondary = null,
	)

	fun presentationForKind(kind: LaprasPulseKind): PulsePresentation = when (kind) {
		LaprasPulseKind.ICE_BEAM_MOVE -> ICE_BEAM_PRESENTATION
		LaprasPulseKind.SHEER_COLD -> SHEER_COLD_PRESENTATION
		LaprasPulseKind.ICE_SHARD -> ICE_SHARD_PRESENTATION
		LaprasPulseKind.HYDRO_PUMP -> HYDRO_PRESENTATION
		LaprasPulseKind.BUBBLE_BEAM_WATER -> BUBBLE_BEAM_PRESENTATION
		LaprasPulseKind.WATER_GUN_OFFTYPE -> WATER_GUN_PRESENTATION
	}

	fun resolveShotProfile(pokemon: PokemonEntity): LaprasShotProfile? {
		val move = pokemon.pokemon.moveSet.getMovesWithNulls().getOrNull(0) ?: return null
		val name = move.template.name

		if (name.equals(HYDRO_PUMP_NAME, ignoreCase = true)) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.HYDRO_PUMP,
				presentation = HYDRO_PRESENTATION,
				rangedMinBlocks = 10.0,
				rangedMaxBlocks = 50.0,
				projectileSpeed = 16f,
				inaccuracy = 0f,
				impactPadTicks = 9,
			)
		}

		if (name.equals(ICE_BEAM_NAME, ignoreCase = true)) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.ICE_BEAM_MOVE,
				presentation = ICE_BEAM_PRESENTATION,
				rangedMinBlocks = 10.0,
				rangedMaxBlocks = 60.0,
				projectileSpeed = 7.25f,
				inaccuracy = 3.5f,
			)
		}

		if (name.equals(SHEER_COLD_NAME, ignoreCase = true)) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.SHEER_COLD,
				presentation = SHEER_COLD_PRESENTATION,
				rangedMinBlocks = 1.0,
				rangedMaxBlocks = 20.0,
				projectileSpeed = 8.5f,
				inaccuracy = 4f,
				impactPadTicks = 12,
			)
		}

		if (move.type == ElementalTypes.WATER) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.BUBBLE_BEAM_WATER,
				presentation = BUBBLE_BEAM_PRESENTATION,
				rangedMinBlocks = 1.0,
				rangedMaxBlocks = 40.0,
				projectileSpeed = 6.5f,
				inaccuracy = 3.5f,
			)
		}

		if (move.type == ElementalTypes.ICE) {
			return LaprasShotProfile(
				kind = LaprasPulseKind.ICE_SHARD,
				presentation = ICE_SHARD_PRESENTATION,
				rangedMinBlocks = 5.0,
				rangedMaxBlocks = 30.0,
				projectileSpeed = 8.25f,
				inaccuracy = 2.5f,
				impactPadTicks = 14,
			)
		}

		return LaprasShotProfile(
			kind = LaprasPulseKind.WATER_GUN_OFFTYPE,
			presentation = WATER_GUN_PRESENTATION,
			rangedMinBlocks = 5.0,
			rangedMaxBlocks = 30.0,
			projectileSpeed = 7f,
			inaccuracy = 3f,
		)
	}
}
