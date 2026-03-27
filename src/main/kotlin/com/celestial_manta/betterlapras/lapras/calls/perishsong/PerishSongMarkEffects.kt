package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

object PerishSongMarkEffects {
	fun applyBaseMarkEffects(entity: LivingEntity) {
		entity.addEffect(
			MobEffectInstance(MobEffects.GLOWING, PerishSongConfig.EFFECT_REFRESH_TICKS, 0, false, false, true),
		)
		entity.addEffect(
			MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PerishSongConfig.EFFECT_REFRESH_TICKS, 1, false, false, true),
		)
	}

	fun clearPerishSongEffects(entity: LivingEntity) {
		entity.removeEffect(MobEffects.GLOWING)
		entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		entity.removeEffect(MobEffects.DARKNESS)
		entity.removeEffect(MobEffects.CONFUSION)
	}
}
