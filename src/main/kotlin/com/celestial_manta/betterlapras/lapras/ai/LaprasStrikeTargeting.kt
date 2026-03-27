package com.celestial_manta.betterlapras.lapras.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Vertical offset above feet for vanilla (non-Cobblemon) strike aim and impact VFX. */
private const val VANILLA_MOB_STRIKE_Y_OFFSET = 0.5

/**
 * Point Lapras move projectiles aim at and impacts use for VFX: Pokémon use eye height;
 * vanilla mobs use [LivingEntity.position] plus [VANILLA_MOB_STRIKE_Y_OFFSET].
 */
internal fun LivingEntity.betterLaprasStrikeTargetPoint(partialTick: Float = 1f): Vec3 =
	if (this is PokemonEntity) getEyePosition(partialTick)
	else position().add(0.0, VANILLA_MOB_STRIKE_Y_OFFSET, 0.0)
