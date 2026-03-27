package com.celestial_manta.betterlapras.lapras.calls.perishsong

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3

object PerishSongFocalAnchor {
	fun anchorFocalLapras(sessions: List<PerishSongSession>, level: ServerLevel) {
		for (session in sessions) {
			if (session.level !== level) continue
			val entity = level.getEntity(session.focalLaprasUuid) as? PokemonEntity ?: continue
			val c = session.center
			entity.setPos(c.x, c.y, c.z)
			entity.deltaMovement = Vec3.ZERO
			(entity as? Mob)?.navigation?.stop()
		}
	}
}
