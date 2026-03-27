package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Extra per-tick behaviors on entities still in the Perish Song mark list (after base glowing/slowness).
 * Append to [layers] to stack effects without editing the handler.
 */
fun interface PerishSongTickLayer {
	fun onStillMarked(level: ServerLevel, session: PerishSongSession, entity: LivingEntity, elapsed: Int)
}

object PerishSongNauseaLayer : PerishSongTickLayer {
	override fun onStillMarked(level: ServerLevel, session: PerishSongSession, entity: LivingEntity, elapsed: Int) {
		// In-game "nausea" is [MobEffects.CONFUSION].
		if (elapsed >= PerishSongConfig.THIRD_WARNING_TICKS && elapsed < 30 * PerishSongConfig.TPS) {
			if (elapsed < PerishSongConfig.NAUSEA_TIER2_START_TICKS) {
				entity.addEffect(
					MobEffectInstance(MobEffects.CONFUSION, PerishSongConfig.EFFECT_REFRESH_TICKS, 5, false, false, true),
				)
			} else {
				entity.addEffect(
					MobEffectInstance(MobEffects.CONFUSION, PerishSongConfig.EFFECT_REFRESH_TICKS, 10, false, false, true),
				)
			}
		}
	}
}

object PerishSongTickLayers {
	val layers: MutableList<PerishSongTickLayer> = mutableListOf(PerishSongNauseaLayer)
}
