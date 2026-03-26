package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chat: same message must contain "Lapras" and "Perish song" (case-insensitive, loose wording).
 * That snapshots a center and marks every [LivingEntity] within
 * [MARK_RADIUS] once (no one who enters later is added): [ServerPlayer]s, vanilla [MobCategory.MONSTER]
 * mobs, and types in tag [HOSTILE_ENTITY_TAG] — never Cobblemon [PokemonEntity]. Marked targets get glowing + Slowness II;
 * players in that initial set hear the custom sting. Leaving [MARK_RADIUS] removes them from the
 * mark list and strips glowing, slowness, darkness, and nausea. At each of the first three milestones
 * (0s / 10s / 20s), marked targets still in range get a darkness pulse and the focal Lapras cry
 * animation; from 20s marked targets get
 * stronger nausea, then after 5s stronger still until 30s; remaining marked entities die. Focal Lapras
 * is never marked. While the song runs, end rod particles form a horizontal ring around the focal
 * Lapras with two concentric rings (opposite transverse wave phase) and forced particle visibility.
 * When a marked target dies (including resolve), an end rod burst plays at its position.
 */
object PerishSongHandler {
	private const val TPS = 20

	/** Initial mark and ongoing range check: leave this cylinder/sphere and you drop off the list. */
	private const val MARK_RADIUS = 50.0
	private val markRadiusSq: Double = MARK_RADIUS * MARK_RADIUS

	/** Warnings at 0s, 10s, 20s (each applies darkness to marked targets); resolve at 30s. */
	private val eventOffsetsTicks = intArrayOf(0, 10 * TPS, 20 * TPS, 30 * TPS)

	private const val COOLDOWN_TICKS = 30 * TPS

	private const val SEARCH_PAD = 256.0

	/** Third milestone (index 2): nausea V from here… */
	private const val THIRD_WARNING_TICKS = 20 * TPS

	/** …until this tick, then nausea X for marked entities until the end. */
	private const val NAUSEA_TIER2_START_TICKS = THIRD_WARNING_TICKS + 5 * TPS

	/** Reapply duration so effects don’t flicker between ticks. */
	private const val EFFECT_REFRESH_TICKS = 6 * TPS

	/** Darkness duration per warning milestone (~3 s). */
	private const val DARKNESS_WARNING_TICKS = 3 * TPS

	/** End rod rings around focal Lapras during Perish Song (XZ circle, Y = transverse displacement). */
	private const val PERISH_RING_RADIUS_INNER = 2.25
	private const val PERISH_RING_RADIUS_OUTER = 3.2
	private const val PERISH_RING_SEGMENTS = 48
	private const val PERISH_WAVE_PEAKS_AROUND_RING = 4.0
	private const val PERISH_WAVE_AMP_BLOCKS = 0.42
	private const val PERISH_RING_Y_OFFSET = 1.05
	/** Phase advance per game tick (wave propagation around the ring). */
	private const val PERISH_WAVE_PHASE_PER_TICK = 0.18

	/** Extra entity types that count as hostile for Perish Song (datapack-extendable). */
	private val HOSTILE_ENTITY_TAG: TagKey<EntityType<*>> = TagKey.create(
		Registries.ENTITY_TYPE,
		ResourceLocation.fromNamespaceAndPath("betterlapras", "hostile"),
	)

	private const val DEATH_BURST_PARTICLES = 36
	private const val DEATH_BURST_SPREAD = 0.9

	private val sessions = Collections.synchronizedList(mutableListOf<Session>())
	private val lastTriggerGameTick = ConcurrentHashMap<UUID, Int>()

	private class Session(
		val level: ServerLevel,
		val center: Vec3,
		val marked: MutableSet<UUID>,
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
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
			val level = entity.level()
			if (level !is ServerLevel) return@register
			val pos = entity.position()
			val uuid = entity.uuid
			synchronized(sessions) {
				for (session in sessions) {
					if (session.level !== level) continue
					if (uuid != session.focalLaprasUuid && uuid in session.marked) {
						spawnEndRodBurst(level, pos)
						break
					}
				}
			}
		}
	}

	private fun onChat(sender: ServerPlayer, message: PlayerChatMessage) {
		val plain = normalizePhrase(message.decoratedContent().string)
		if (!matchesPerishSongChat(plain)) return

		val level = sender.serverLevel()
		val now = level.server.tickCount
		val last = lastTriggerGameTick[sender.uuid] ?: -COOLDOWN_TICKS * 2
		if (now - last < COOLDOWN_TICKS) return

		val lapras = findNearestOwnedLapras(sender) ?: return
		val center = lapras.position()
		val marked = collectMarkedLiving(level, center, lapras.uuid)

		for (uuid in marked) {
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			applyBaseMarkEffects(entity)
		}
		playPerishSongSoundForMarkedPlayers(level, center, marked)

		lastTriggerGameTick[sender.uuid] = now
		synchronized(sessions) {
			sessions.add(Session(level, center, marked, now, lapras.uuid))
		}
	}

	private fun tickWorld(level: ServerLevel) {
		anchorFocalLapras(level)
		tickPerishSongParticles(level)
		val tick = level.server.tickCount
		synchronized(sessions) {
			val iter = sessions.iterator()
			while (iter.hasNext()) {
				val session = iter.next()
				if (session.level !== level) continue
				val elapsed = tick - session.startTick
				tickMarkedEffects(level, session, elapsed)
				while (session.nextMilestoneIndex < eventOffsetsTicks.size &&
					elapsed >= eventOffsetsTicks[session.nextMilestoneIndex]
				) {
					when (session.nextMilestoneIndex) {
						0, 1, 2 -> {
							applyDarknessToMarked(level, session)
							broadcastFocalLaprasCry(level, session)
						}
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

	/**
	 * Two concentric rings in the XZ plane; vertical offset is a sine in [theta] ± phase (outer uses
	 * opposite sign so the wave runs the other way). [sendPerishSongParticleForce] uses always-visible
	 * particles (override distance limiter).
	 */
	private fun tickPerishSongParticles(level: ServerLevel) {
		val t = level.gameTime.toDouble()
		synchronized(sessions) {
			for (session in sessions) {
				if (session.level !== level) continue
				val c = session.center
				val phase = t * PERISH_WAVE_PHASE_PER_TICK
				val n = PERISH_RING_SEGMENTS
				val rings = arrayOf(
					Pair(PERISH_RING_RADIUS_INNER, 1.0),
					Pair(PERISH_RING_RADIUS_OUTER, -1.0),
				)
				for (i in 0 until n) {
					val theta = (i / n.toDouble()) * 2.0 * PI
					for ((radius, phaseSign) in rings) {
						val x = c.x + radius * cos(theta)
						val z = c.z + radius * sin(theta)
						val y = c.y + PERISH_RING_Y_OFFSET +
							PERISH_WAVE_AMP_BLOCKS * sin(
								PERISH_WAVE_PEAKS_AROUND_RING * theta + phaseSign * phase,
							)
						sendPerishSongParticleForce(level, x, y, z)
					}
				}
			}
		}
	}

	private fun sendPerishSongParticleForce(level: ServerLevel, x: Double, y: Double, z: Double) {
		val particle = BetterLaprasParticles.PERISH_SONG_END_ROD
		for (player in level.players()) {
			level.sendParticles(player, particle, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
		}
	}

	private fun spawnEndRodBurst(level: ServerLevel, pos: Vec3) {
		val r = level.random
		repeat(DEATH_BURST_PARTICLES) {
			val ox = (r.nextDouble() - 0.5) * 2.0 * DEATH_BURST_SPREAD
			val oy = (r.nextDouble() - 0.5) * 2.0 * DEATH_BURST_SPREAD
			val oz = (r.nextDouble() - 0.5) * 2.0 * DEATH_BURST_SPREAD
			sendPerishSongParticleForce(level, pos.x + ox, pos.y + oy + 0.4, pos.z + oz)
		}
	}

	private fun anchorFocalLapras(level: ServerLevel) {
		synchronized(sessions) {
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

	/**
	 * Keeps glowing + slowness on still-marked targets; drops targets that leave [MARK_RADIUS] or unload;
	 * applies nausea V from the third warning until [NAUSEA_TIER2_START_TICKS], then nausea X until 30s.
	 */
	private fun tickMarkedEffects(level: ServerLevel, session: Session, elapsed: Int) {
		val center = session.center
		val markIt = session.marked.iterator()
		while (markIt.hasNext()) {
			val uuid = markIt.next()
			val entity = level.getEntity(uuid) as? LivingEntity
			if (entity == null || !entity.isAlive) {
				markIt.remove()
				continue
			}
			if (entity.position().distanceToSqr(center) > markRadiusSq) {
				clearPerishSongEffects(entity)
				markIt.remove()
				continue
			}
			applyBaseMarkEffects(entity)
			// In-game "nausea" is [MobEffects.CONFUSION].
			if (elapsed >= THIRD_WARNING_TICKS && elapsed < 30 * TPS) {
				if (elapsed < NAUSEA_TIER2_START_TICKS) {
					entity.addEffect(
						MobEffectInstance(MobEffects.CONFUSION, EFFECT_REFRESH_TICKS, 5, false, false, true),
					)
				} else {
					entity.addEffect(
						MobEffectInstance(MobEffects.CONFUSION, EFFECT_REFRESH_TICKS, 10, false, false, true),
					)
				}
			}
		}
	}

	private fun applyBaseMarkEffects(entity: LivingEntity) {
		entity.addEffect(MobEffectInstance(MobEffects.GLOWING, EFFECT_REFRESH_TICKS, 0, false, false, true))
		entity.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_TICKS, 1, false, false, true))
	}

	private fun clearPerishSongEffects(entity: LivingEntity) {
		entity.removeEffect(MobEffects.GLOWING)
		entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
		entity.removeEffect(MobEffects.DARKNESS)
		entity.removeEffect(MobEffects.CONFUSION)
	}

	private fun broadcastFocalLaprasCry(level: ServerLevel, session: Session) {
		val lapras = level.getEntity(session.focalLaprasUuid) as? PokemonEntity ?: return
		LaprasPosableAnimationPackets.broadcastLaprasCry(lapras)
	}

	/** One of three darkness pulses at 0s / 10s / 20s for targets still marked and in range. */
	private fun applyDarknessToMarked(level: ServerLevel, session: Session) {
		for (uuid in session.marked.toList()) {
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			if (entity.position().distanceToSqr(session.center) > markRadiusSq) continue
			entity.addEffect(
				MobEffectInstance(MobEffects.DARKNESS, DARKNESS_WARNING_TICKS, 0, false, false, true),
			)
		}
	}

	private fun playPerishSongSoundForMarkedPlayers(level: ServerLevel, at: Vec3, marked: Set<UUID>) {
		val holder: Holder<SoundEvent> = Holder.direct(BetterLaprasSounds.PERISH_SONG)
		val maxD = MARK_RADIUS
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

	private fun resolve(session: Session) {
		val level = session.level
		for (uuid in session.marked.toList()) {
			if (uuid == session.focalLaprasUuid) continue
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			entity.kill()
		}
	}

	private fun collectMarkedLiving(level: ServerLevel, center: Vec3, excludeUuid: UUID): MutableSet<UUID> {
		val box = AABB(center, center).inflate(MARK_RADIUS)
		val out = mutableSetOf<UUID>()
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeUuid) continue
			if (entity.position().distanceToSqr(center) > markRadiusSq) continue
			if (!isPerishSongTarget(entity)) continue
			out.add(entity.uuid)
		}
		return out
	}

	/**
	 * Players, vanilla [MobCategory.MONSTER] mobs, and [HOSTILE_ENTITY_TAG]; never other Pokémon.
	 */
	private fun isPerishSongTarget(entity: LivingEntity): Boolean {
		if (entity is ServerPlayer) return true
		if (entity is PokemonEntity) return false
		val t = entity.type
		if (t.`is`(HOSTILE_ENTITY_TAG)) return true
		return t.category == MobCategory.MONSTER
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

	/** True if the normalized chat line contains both "lapras" and "perish song" (same message). */
	private fun matchesPerishSongChat(normalized: String): Boolean =
		normalized.contains("lapras") && normalized.contains("perish song")
}
