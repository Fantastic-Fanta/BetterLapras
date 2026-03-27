package com.celestial_manta.betterlapras.lapras.calls.perishsong

import com.celestial_manta.betterlapras.BetterLaprasSounds
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

object PerishSongAudio {
	fun playStingForMarkedPlayers(level: ServerLevel, at: Vec3, marked: Set<UUID>) {
		val holder: Holder<SoundEvent> = Holder.direct(BetterLaprasSounds.PERISH_SONG)
		val maxD = PerishSongConfig.MARK_RADIUS
		val maxDSq = maxD * maxD
		for (uuid in marked) {
			val player = level.getEntity(uuid) as? ServerPlayer ?: continue
			if (player.position().distanceToSqr(at) > maxDSq) continue
			val dist = sqrt(player.position().distanceToSqr(at))
			val factor = (1.0 - dist / maxD).toFloat().coerceIn(0f, 1f)
			player.connection.send(
				ClientboundSoundPacket(
					holder,
					SoundSource.NEUTRAL,
					at.x,
					at.y,
					at.z,
					factor,
					1.0f,
					level.random.nextLong(),
				),
			)
		}
	}
}
