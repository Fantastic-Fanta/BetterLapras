package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID

data class PerishSongSession(
	val level: ServerLevel,
	val center: Vec3,
	val marked: MutableSet<UUID>,
	val startTick: Int,
	val focalLaprasUuid: UUID,
) {
	var nextMilestoneIndex: Int = 0
}
