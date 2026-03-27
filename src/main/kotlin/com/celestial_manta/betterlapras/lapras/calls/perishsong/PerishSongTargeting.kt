package com.celestial_manta.betterlapras.lapras.calls.perishsong

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object PerishSongTargeting {
	fun collectMarkedLiving(level: ServerLevel, center: Vec3, excludeUuid: UUID): MutableSet<UUID> {
		val box = AABB(center, center).inflate(PerishSongConfig.MARK_RADIUS)
		val out = mutableSetOf<UUID>()
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeUuid) continue
			if (entity.position().distanceToSqr(center) > PerishSongConfig.markRadiusSq) continue
			if (!isPerishSongTarget(entity)) continue
			out.add(entity.uuid)
		}
		return out
	}

	/**
	 * Players, vanilla [MobCategory.MONSTER] mobs, and [PerishSongConfig.HOSTILE_ENTITY_TAG]; never other Pokémon.
	 */
	fun isPerishSongTarget(entity: LivingEntity): Boolean {
		if (entity is ServerPlayer) return true
		if (entity is PokemonEntity) return false
		val t = entity.type
		if (t.`is`(PerishSongConfig.HOSTILE_ENTITY_TAG)) return true
		return t.category == MobCategory.MONSTER
	}

	fun findNearestOwnedLapras(player: ServerPlayer): PokemonEntity? {
		val level = player.level() as? ServerLevel ?: return null
		val ownerId = player.uuid
		val box = player.boundingBox.inflate(PerishSongConfig.SEARCH_PAD)
		var best: PokemonEntity? = null
		var bestD = Double.MAX_VALUE
		for (entity in level.getEntitiesOfClass(PokemonEntity::class.java, box)) {
			if (entity.ownerUUID != ownerId) continue
			if (entity.pokemon.species.resourceIdentifier.path != "lapras") continue
			val d = entity.distanceToSqr(player)
			if (d < bestD) {
				bestD = d
				best = entity
			}
		}
		return best
	}
}
