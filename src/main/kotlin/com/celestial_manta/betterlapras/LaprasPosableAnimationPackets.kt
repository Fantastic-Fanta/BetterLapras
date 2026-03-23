package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import java.util.function.Predicate

/**
 * ## Why “Bedrock” shows up in Java code
 *
 * Cobblemon is a **Java Edition** mod, but each species is authored with **Bedrock Edition–style** assets:
 * Blockbench geo/animation JSON and **Molang**. The JVM code only sends **packets**; the **client** resolves
 * names like `"status"` through that poser + model.
 *
 * ## Delivery (important)
 *
 * [PokemonEntity.cry] does **not** use [PlayPosableAnimationPacket.sendToPlayersAround]. It finds nearby
 * [ServerPlayer]s with [net.minecraft.world.level.Level.getEntitiesOfClass] in an [AABB] and sends via
 * [CobblemonNetwork.sendPacketToPlayer]. We mirror that so packets actually reach clients the same way cry does.
 *
 * ## Why battle looked fine but overworld did not
 *
 * On the client, Cobblemon’s `PosableModel` resolves names by the **current pose’s** `namedAnimations` map
 * first, then the poser root `animations` map. Vanilla Lapras only defines **`status`** / **`special`** under
 * battle poses; overworld poses omit them, so those names used to resolve to nothing outside battle.
 *
 * The logical keys are always **`status`** and **`special`** — water vs land is chosen inside each pose’s
 * molang (`bedrock_primary(..., 'water_status', ...)` etc.). Packets must use those keys, **not** Bedrock
 * animation ids like `water_status`.
 *
 * [broadcastLaprasCryPose] uses **`cry`** (same as [PokemonEntity.cry]).
 * [broadcastLaprasStatusMove] sends **`status`** then **`special`** only.
 */
object LaprasPosableAnimationPackets {
	/** Half-extents for [AABB.ofSize] — same pattern as cry (64) but wider for following Pokémon. */
	private const val PLAYER_BOX_HALF_EXTENT = 96.0

	private val anyPlayer: Predicate<ServerPlayer> = Predicate { true }

	private fun sendPlayPosablePacketToNearbyPlayers(entity: PokemonEntity, animationNames: Set<String>) {
		if (entity.level().isClientSide) return
		val sl = entity.level() as? ServerLevel ?: return
		val packet = PlayPosableAnimationPacket(entity.id, animationNames, emptyList())
		val box = AABB.ofSize(entity.position(), PLAYER_BOX_HALF_EXTENT, PLAYER_BOX_HALF_EXTENT, PLAYER_BOX_HALF_EXTENT)
		val players = sl.getEntitiesOfClass(ServerPlayer::class.java, box, anyPlayer)
		for (player in players) {
			CobblemonNetwork.sendPacketToPlayer(player, packet)
		}
	}

	/** Sing: same primary name Cobblemon uses for [PokemonEntity.cry] (`cry` in top-level poser `animations`). */
	@JvmStatic
	fun broadcastLaprasCryPose(entity: PokemonEntity) {
		sendPlayPosablePacketToNearbyPlayers(entity, setOf("cry"))
		entity.playAnimation("cry", listOf())
	}

	@JvmStatic
	fun broadcastLaprasStatusMove(entity: PokemonEntity) {
		val names = linkedSetOf("status", "special")
		sendPlayPosablePacketToNearbyPlayers(entity, names)
		entity.playAnimation("status", listOf())
	}
}
