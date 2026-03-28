package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.celestial_manta.betterlapras.BetterLaprasParticles
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.JvmStatic
import kotlin.math.max

/**
 * Gigantamax Lapras: one beam-textured force-field ring per entity, respawned each time the client
 * particle’s 6000-tick lifetime elapses so the effect stays continuous while Gmax is active.
 */
object GmaxLaprasParticles {
	private const val RING_PARTICLE_LIFETIME_TICKS = 6000L
	private const val RESPAWN_INTERVAL_TICKS = RING_PARTICLE_LIFETIME_TICKS

	/** Mega Showdown’s team — same as `/team join glow_dynamax_red …`; Glowing outline follows team color. */
	private const val GLOW_DYNAMAX_RED_TEAM = "glow_dynamax_red"

	private val lastRingSpawnGameTime = ConcurrentHashMap<UUID, Long>()

	@JvmStatic
	fun tick(entity: PokemonEntity) {
		if (entity.level().isClientSide) return
		val level = entity.level() as? ServerLevel ?: return
		if (entity.pokemon.species.resourceIdentifier.path != "lapras") return
		val uuid = entity.uuid
		val server = level.server
		val cmd = server.createCommandSourceStack().withPermission(4)
		val member = entity.scoreboardName
		val teams = level.scoreboard
		val currentTeam = teams.getPlayersTeam(member)
		val glowTeam = teams.getPlayerTeam(GLOW_DYNAMAX_RED_TEAM)
		if ("gmax" !in entity.pokemon.aspects) {
			if (currentTeam != null && currentTeam.name == GLOW_DYNAMAX_RED_TEAM) {
				server.commands.performPrefixedCommand(cmd, "team leave $member")
			}
			lastRingSpawnGameTime.remove(uuid)
			return
		}
		if (glowTeam != null && currentTeam != glowTeam) {
			server.commands.performPrefixedCommand(cmd, "team join $GLOW_DYNAMAX_RED_TEAM $member")
		}
		val now = level.gameTime
		val last = lastRingSpawnGameTime[uuid]
		if (last != null && now - last < RESPAWN_INTERVAL_TICKS) return
		lastRingSpawnGameTime[uuid] = now
		val x = entity.x
		val y = entity.y
		val z = entity.z
		val bb = entity.boundingBox
		val horizontalHalf = max(bb.maxX - bb.minX, bb.maxZ - bb.minZ) * 0.5
		val ringRadius = max(
			GmaxLaprasParticleConfig.RING_RADIUS_OUTER,
			horizontalHalf * GmaxLaprasParticleConfig.FORCE_FIELD_RING_BB_HORIZONTAL_MARGIN,
		)
		val yOffset = max(
			GmaxLaprasParticleConfig.RING_Y_OFFSET,
			(bb.maxY - bb.minY) * GmaxLaprasParticleConfig.FORCE_FIELD_RING_BB_VERTICAL_FRAC,
		)
		// ClientboundLevelParticlesPacket stores delta/speed as floats. Count must be 0 so the client
		// uses maxSpeed*delta as velocity (deterministic). Count >= 1 applies Gaussian spread and
		// destroys ring radius / entity id. Entity id is sent as a float (exact for typical ids < 2^24).
		for (player in level.players()) {
			level.sendParticles(
				player,
				BetterLaprasParticles.GMAX_FORCE_FIELD_RING,
				true,
				x,
				y,
				z,
				0,
				ringRadius,
				entity.id.toDouble(),
				yOffset,
				1.0,
			)
		}
	}
}
