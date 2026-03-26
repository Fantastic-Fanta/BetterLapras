package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.util.function.Predicate

/**
 * Sends [PlayPosableAnimationPacket] the same way [com.cobblemon.mod.common.entity.pokemon.PokemonEntity.cry]
 * does (64 half-extent [AABB], [Level.getEntitiesOfClass], all players, [CobblemonNetwork.sendPacketToPlayer]).
 * Sing reuses the **`cry`** logical animation (same Bedrock cry clips as vanilla Lapras cry).
 */
object LaprasPosableAnimationPackets {
	private const val CRY_STYLE_BOX_HALF_EXTENT = 64.0

	private val anyServerPlayer: Predicate<ServerPlayer> = Predicate { true }

	private fun broadcastPlayPosableLikeCry(entity: PokemonEntity, animationNames: Set<String>) {
		if (entity.level().isClientSide) return
		val packet = PlayPosableAnimationPacket(entity.id, animationNames, emptyList())
		val box = AABB.ofSize(
			entity.position(),
			CRY_STYLE_BOX_HALF_EXTENT,
			CRY_STYLE_BOX_HALF_EXTENT,
			CRY_STYLE_BOX_HALF_EXTENT,
		)
		val players = entity.level().getEntitiesOfClass(ServerPlayer::class.java, box, anyServerPlayer)
		for (player in players) {
			CobblemonNetwork.sendPacketToPlayer(player, packet)
		}
	}

	@JvmStatic
	fun broadcastLaprasSing(entity: PokemonEntity) {
		broadcastPlayPosableLikeCry(entity, setOf("cry"))
	}

	@JvmStatic
	fun broadcastLaprasCry(entity: PokemonEntity) {
		broadcastLaprasSing(entity)
	}
}
