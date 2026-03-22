package com.celestial_manta.betterlapras

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Ice Beam: heavy Slowness before the beam + Slowness I after hit.
 * Ice Shard (generic ice): Slowness I on hit only (no pre-freeze).
 */
object LaprasIceBeamEffects {
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

	fun clearHeavySlownessFromMiss(target: LivingEntity?) {
		val mob = target ?: return
		if (mob.level().isClientSide) return
		val inst = mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN) ?: return
		if (inst.amplifier >= FREEZE_AMPLIFIER) {
			mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		}
	}
}
