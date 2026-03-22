package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/** True when move slot 0 exists and is an Ice-type move (not Water / Hydro / off-type water). */
internal fun PokemonEntity.slot1MoveIsIceType(): Boolean {
	val m = pokemon.moveSet.getMovesWithNulls().getOrNull(0) ?: return false
	return m.type == ElementalTypes.ICE
}

/**
 * Ice slot-1 beam: heavy Slowness during wind-up/travel, then Slowness I for a few seconds on hit.
 * Misses clear the heavy Slowness so targets are not left perma-frozen.
 */
object LaprasIceBeamEffects {
	/** Slowness amplifier 99 → UI level 100. */
	private const val FREEZE_AMPLIFIER = 99

	private const val FREEZE_BUFFER_TICKS = 20
	private const val POST_HIT_SLOW_TICKS = 60
	private const val POST_HIT_SLOW_AMPLIFIER = 0

	fun applyPreBeamFreeze(target: LivingEntity, impactDelayTicks: Int) {
		if (target.level().isClientSide) return
		val duration = impactDelayTicks + FREEZE_BUFFER_TICKS
		target.addEffect(
			MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN,
				duration,
				FREEZE_AMPLIFIER,
				false,
				true,
				true,
			),
		)
	}

	fun applyPostHitSlow(target: LivingEntity) {
		if (target.level().isClientSide) return
		target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		target.addEffect(
			MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN,
				POST_HIT_SLOW_TICKS,
				POST_HIT_SLOW_AMPLIFIER,
				false,
				true,
				true,
			),
		)
	}

	/** Called when an ice pulse misses (e.g. block); only strips our freeze tier. */
	fun clearHeavySlownessFromMiss(target: LivingEntity?) {
		val mob = target ?: return
		if (mob.level().isClientSide) return
		val inst = mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN) ?: return
		if (inst.amplifier >= FREEZE_AMPLIFIER) {
			mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		}
	}
}
