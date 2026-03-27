package com.celestial_manta.betterlapras.lapras.moves.effects

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Ice Beam: heavy Slowness on target before the beam, Slowness XX (level 20) on Lapras for the same window,
 * then Slowness I on the target after hit. Ice Shard: Slowness I on hit only (no pre-freeze).
 */
object LaprasIceBeamEffects {
	private const val FREEZE_AMPLIFIER = 99
	private const val FREEZE_BUFFER_TICKS = 20
	/** Slowness level 20 (amplifier is zero-based). */
	private const val SELF_PREFREEZE_SLOW_AMPLIFIER = 19
	private const val POST_HIT_SLOW_TICKS = 60
	private const val POST_HIT_SLOW_AMPLIFIER = 0

	fun applyPreBeamFreeze(target: LivingEntity, impactDelayTicks: Int, self: LivingEntity? = null) {
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
		val actor = self ?: return
		if (actor.level().isClientSide) return
		actor.addEffect(
			MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN,
				duration,
				SELF_PREFREEZE_SLOW_AMPLIFIER,
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

	fun clearHeavySlownessFromMiss(target: LivingEntity?, owner: LivingEntity? = null) {
		val mob = target
		if (mob != null && !mob.level().isClientSide) {
			val inst = mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN)
			if (inst != null && inst.amplifier >= FREEZE_AMPLIFIER) {
				mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
			}
		}
		clearPreBeamSelfSlow(owner)
	}

	fun clearPreBeamSelfSlow(owner: LivingEntity?) {
		val mob = owner ?: return
		if (mob.level().isClientSide) return
		val inst = mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN) ?: return
		if (inst.amplifier == SELF_PREFREEZE_SLOW_AMPLIFIER) {
			mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		}
	}
}
