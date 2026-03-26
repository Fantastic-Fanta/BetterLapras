package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Chat phrase "Lapras, use Perish Song" triggers a fixed world-space sphere (20 blocks) at the
 * nearest owned Lapras position at trigger time; darkness warnings at 0s / 10s / 20s,
 * custom [BetterLaprasSounds.PERISH_SONG] + Lapras cry on the first warning; from 25s–29s nausea (Confusion)
 * ramps amplifier 1→20; kill at 30s. [LivingEntity] in the sphere are targeted (Lapras never marked).
 */
object PerishSongHandler {
	private const val RADIUS = 20.0
	private val radiusSq: Double = RADIUS * RADIUS

	private const val TPS = 20

	/** Warnings at 0s, 10s, 20s; kill at 30s. */
	private val eventOffsetsTicks = intArrayOf(0, 10 * TPS, 20 * TPS, 30 * TPS)

	private const val COOLDOWN_TICKS = 30 * TPS

	private const val SEARCH_PAD = 256.0

	/** Darkness duration per warning (~3 s). */
	private const val DARKNESS_WARNING_TICKS = 3 * 20

	/** Living entities within this padding of [center] receive darkness with each warning. */
	private const val DARKNESS_PADDING = 8.0

	/** Nausea ramp: second 25 through end of second 29 (100 ticks), amplifiers 1..20. */
	private const val NAUSEA_RAMP_START_TICKS = 25 * TPS
	private const val NAUSEA_RAMP_END_EXCLUSIVE = 30 * TPS

	private const val NAUSEA_RAMP_REFRESH_TICKS = 25

	private val sessions = Collections.synchronizedList(mutableListOf<Session>())
	private val lastTriggerGameTick = ConcurrentHashMap<UUID, Int>()

	private class Session(
		val level: ServerLevel,
		val center: Vec3,
		val marked: Set<UUID>,
		val startTick: Int,
		val focalLaprasUuid: UUID,
	) {
		var nextMilestoneIndex: Int = 0
	}

	fun register() {
		ServerMessageEvents.CHAT_MESSAGE.register { message: PlayerChatMessage, sender: ServerPlayer, _ ->
			onChat(sender, message)
		}
		ServerTickEvents.END_WORLD_TICK.register { world ->
			if (world is ServerLevel) {
				tickWorld(world)
			}
		}
	}

	private fun onChat(sender: ServerPlayer, message: PlayerChatMessage) {
		val plain = normalizePhrase(message.decoratedContent().string)
		if (plain != TRIGGER_PHRASE) return

		val level = sender.serverLevel()
		val now = level.server.tickCount
		val last = lastTriggerGameTick[sender.uuid] ?: -COOLDOWN_TICKS * 2
		if (now - last < COOLDOWN_TICKS) return

		val lapras = findNearestOwnedLapras(sender) ?: return
		val center = lapras.position()
		val marked = collectMarkedLiving(level, center, lapras.uuid)

		lastTriggerGameTick[sender.uuid] = now
		synchronized(sessions) {
			sessions.add(Session(level, center, marked, now, lapras.uuid))
		}
	}

	private fun tickWorld(level: ServerLevel) {
		val tick = level.server.tickCount
		synchronized(sessions) {
			val iter = sessions.iterator()
			while (iter.hasNext()) {
				val session = iter.next()
				if (session.level !== level) continue
				val elapsed = tick - session.startTick
				tickNauseaRamp(level, session.center, session.focalLaprasUuid, elapsed)
				while (session.nextMilestoneIndex < eventOffsetsTicks.size &&
					elapsed >= eventOffsetsTicks[session.nextMilestoneIndex]
				) {
					when (session.nextMilestoneIndex) {
						0, 1, 2 -> playWarning(level, session.center, session.nextMilestoneIndex, session.focalLaprasUuid)
						3 -> resolve(session)
					}
					session.nextMilestoneIndex++
				}
				if (session.nextMilestoneIndex >= eventOffsetsTicks.size) {
					iter.remove()
				}
			}
		}
	}

	private fun playWarning(level: ServerLevel, center: Vec3, index: Int, focalLaprasUuid: UUID) {
		if (index == 0) {
			val lapras = level.getEntity(focalLaprasUuid) as? PokemonEntity
			val at = lapras?.position() ?: center
			playPerishSongSound(level, at)
			if (lapras != null) {
				LaprasPosableAnimationPackets.broadcastLaprasCry(lapras)
			}
		}
		applyWarningDarkness(level, center, focalLaprasUuid)
	}

	private fun playPerishSongSound(level: ServerLevel, at: Vec3) {
		level.playSound(null, at.x, at.y, at.z, BetterLaprasSounds.PERISH_SONG, SoundSource.NEUTRAL, 1.0f, 1.0f)
	}

	private fun applyWarningDarkness(level: ServerLevel, center: Vec3, excludeLaprasUuid: UUID) {
		val r = RADIUS + DARKNESS_PADDING
		val rsq = r * r
		val box = AABB(center, center).inflate(r)
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeLaprasUuid) continue
			if (entity.position().distanceToSqr(center) > rsq) continue
			entity.addEffect(
				MobEffectInstance(MobEffects.DARKNESS, DARKNESS_WARNING_TICKS, 0, false, false, true),
			)
		}
	}

	/**
	 * [elapsed] 500–599: nausea amplifier 1→20 over the 25s–29s window ([MobEffects.CONFUSION]).
	 */
	private fun tickNauseaRamp(level: ServerLevel, center: Vec3, excludeLaprasUuid: UUID, elapsed: Int) {
		if (elapsed < NAUSEA_RAMP_START_TICKS || elapsed >= NAUSEA_RAMP_END_EXCLUSIVE) return
		val span = NAUSEA_RAMP_END_EXCLUSIVE - NAUSEA_RAMP_START_TICKS
		val t = elapsed - NAUSEA_RAMP_START_TICKS
		val denom = max(1, span - 1)
		val amplifier = Mth.clamp(1 + (t * 19) / denom, 1, 20)
		val box = AABB(center, center).inflate(RADIUS)
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeLaprasUuid) continue
			if (entity.position().distanceToSqr(center) > radiusSq) continue
			entity.addEffect(
				MobEffectInstance(
					MobEffects.CONFUSION,
					NAUSEA_RAMP_REFRESH_TICKS,
					amplifier,
					false,
					false,
					true,
				),
			)
		}
	}

	private fun resolve(session: Session) {
		val level = session.level
		for (uuid in session.marked) {
			if (uuid == session.focalLaprasUuid) continue
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			if (entity.position().distanceToSqr(session.center) > radiusSq) continue
			entity.kill()
		}
	}

	private fun collectMarkedLiving(level: ServerLevel, center: Vec3, excludeUuid: UUID): Set<UUID> {
		val box = AABB(center, center).inflate(RADIUS)
		val out = mutableSetOf<UUID>()
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeUuid) continue
			if (entity.position().distanceToSqr(center) <= radiusSq) {
				out.add(entity.uuid)
			}
		}
		return out
	}

	private fun findNearestOwnedLapras(player: ServerPlayer): PokemonEntity? {
		val level = player.level() as? ServerLevel ?: return null
		val ownerId = player.uuid
		val box = player.boundingBox.inflate(SEARCH_PAD)
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

	private fun normalizePhrase(s: String): String =
		s.trim().lowercase().replace(Regex("\\s+"), " ")

	private const val TRIGGER_PHRASE = "lapras, use perish song"
}
