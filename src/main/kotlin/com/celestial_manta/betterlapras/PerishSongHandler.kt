package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chat phrase "Lapras, use Perish Song" triggers a fixed world-space sphere (20 blocks) at the
 * nearest owned Lapras position at trigger time; three warden-style warnings at 5 s intervals
 * (sound + darkness + Lapras cry on first warning), ongoing slowness/nausea in the zone
 * (hidden particles, Lapras excluded), transverse-wave note rings (few layers, long-distance
 * particles), occasional notes near Lapras’ body, then [LivingEntity] instances still inside the
 * sphere are killed (Lapras never marked).
 */
object PerishSongHandler {
	private const val RADIUS = 20.0
	private val radiusSq: Double = RADIUS * RADIUS

	private const val WARNING_INTERVAL_TICKS = 5 * 20
	private val eventOffsetsTicks = intArrayOf(0, WARNING_INTERVAL_TICKS, WARNING_INTERVAL_TICKS * 2, WARNING_INTERVAL_TICKS * 3)

	private const val COOLDOWN_TICKS = 30 * 20

	private const val SEARCH_PAD = 256.0

	/** Darkness duration per warning (~3 s). */
	private const val DARKNESS_WARNING_TICKS = 3 * 20

	/** Living entities within this padding of [center] receive darkness with each warning. */
	private const val DARKNESS_PADDING = 8.0

	/** How long the note ring keeps spawning (matches full countdown until resolve). */
	private const val NOTE_PULSE_DURATION_TICKS = WARNING_INTERVAL_TICKS * 3

	/** Slower time phase for the transverse wave (higher = slower undulation). */
	private const val VERTICAL_PULSE_PERIOD_TICKS = 100

	/** Peak vertical offset (blocks) for the transverse wave on each ring sample. */
	private const val VERTICAL_PULSE_AMPLITUDE = 0.38

	/** Number of wave peaks around the ring circumference (transverse / traveling appearance). */
	private const val WAVES_AROUND_RING = 4

	/** Concentric note rings at slightly different radii. */
	private const val NOTE_RING_LAYERS = 3

	private const val LAYER_RADIUS_STEP = 0.5

	/** Samples around each ring layer (lower = sparser ring). */
	private const val NOTE_CIRCLE_SEGMENTS = 48

	/** How often a few notes pop around Lapras’ body (≈1/s). */
	private const val LAPRAS_NOTE_INTERVAL_TICKS = 20

	/** Notes spawned per burst near Lapras. */
	private const val LAPRAS_NOTES_PER_BURST = 4

	private const val AREA_EFFECT_REFRESH_TICKS = 25

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
				val lapras = level.getEntity(session.focalLaprasUuid) as? PokemonEntity

				if (elapsed >= 0 && elapsed < NOTE_PULSE_DURATION_TICKS) {
					spawnNoteRingPulseFrame(level, session.center, elapsed)
					if (lapras != null) {
						spawnFloatingNotesAroundLapras(level, lapras, elapsed)
					}
					tickAreaDebuffs(level, session.center, session.focalLaprasUuid)
				}
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
		val sound = when (index) {
			0 -> SoundEvents.WARDEN_NEARBY_CLOSE
			1 -> SoundEvents.WARDEN_NEARBY_CLOSER
			else -> SoundEvents.WARDEN_NEARBY_CLOSEST
		}
		level.playSound(null, center.x, center.y, center.z, sound, SoundSource.HOSTILE, 3.0f, 1.0f)
		applyWarningDarkness(level, center, focalLaprasUuid)
		if (index == 0) {
			val lapras = level.getEntity(focalLaprasUuid) as? PokemonEntity
			if (lapras != null) {
				LaprasPosableAnimationPackets.broadcastLaprasCry(lapras)
			}
		}
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
	 * Slowness I + Nausea I while inside the fixed sphere; hidden effect particles.
	 * Refreshes while the song is active so leaving clears naturally.
	 */
	private fun tickAreaDebuffs(level: ServerLevel, center: Vec3, excludeLaprasUuid: UUID) {
		val box = AABB(center, center).inflate(RADIUS)
		for (entity in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (entity.uuid == excludeLaprasUuid) continue
			if (entity.position().distanceToSqr(center) > radiusSq) continue
			entity.addEffect(
				MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AREA_EFFECT_REFRESH_TICKS, 0, false, false, true),
			)
			entity.addEffect(
				MobEffectInstance(MobEffects.CONFUSION, AREA_EFFECT_REFRESH_TICKS, 0, false, false, true),
			)
		}
	}

	/**
	 * Multiple concentric rings; each sample uses a transverse wave (phase from time + angle around
	 * the ring) so the pattern reads as a wave traveling around the zone.
	 */
	private fun spawnNoteRingPulseFrame(level: ServerLevel, center: Vec3, elapsedTicks: Int) {
		val period = VERTICAL_PULSE_PERIOD_TICKS.toDouble()
		val timePhase = 2.0 * PI * elapsedTicks / period
		for (layer in 0 until NOTE_RING_LAYERS) {
			val layerOffset = (layer - (NOTE_RING_LAYERS - 1) / 2.0) * LAYER_RADIUS_STEP
			val layerRadius = RADIUS + layerOffset
			val layerPhase = 2.0 * PI * layer / NOTE_RING_LAYERS
			for (i in 0 until NOTE_CIRCLE_SEGMENTS) {
				val angle = (i.toDouble() / NOTE_CIRCLE_SEGMENTS) * 2.0 * PI
				val x = center.x + layerRadius * cos(angle)
				val z = center.z + layerRadius * sin(angle)
				val yOffset = VERTICAL_PULSE_AMPLITUDE * sin(timePhase + WAVES_AROUND_RING * angle + layerPhase)
				val y = center.y + 0.125 + yOffset
				val noteColor = ((i + elapsedTicks / 4 + layer * 3) % 25) / 24.0
				sendNoteParticleLongDistance(level, x, y, z, noteColor)
			}
		}
	}

	/**
	 * Sends a note particle with the packet’s limiter overridden so clients render it up to 512 blocks
	 * away (vanilla [ServerLevel.sendParticles] without this caps around 32 blocks).
	 */
	private fun sendNoteParticleLongDistance(level: ServerLevel, x: Double, y: Double, z: Double, noteColor: Double) {
		for (player in level.players()) {
			level.sendParticles(player, ParticleTypes.NOTE, true, x, y, z, 1, noteColor, 0.0, 0.0, 0.5)
		}
	}

	/** A few notes near Lapras’ body, once per [LAPRAS_NOTE_INTERVAL_TICKS]. */
	private fun spawnFloatingNotesAroundLapras(level: ServerLevel, lapras: PokemonEntity, elapsedTicks: Int) {
		if (elapsedTicks % LAPRAS_NOTE_INTERVAL_TICKS != 0) return
		val rng = lapras.random
		val base = lapras.position()
		val h = lapras.bbHeight
		repeat(LAPRAS_NOTES_PER_BURST) {
			val angle = rng.nextDouble() * 2.0 * PI
			val r = 0.55 + rng.nextDouble() * 1.0
			val x = base.x + r * cos(angle)
			val z = base.z + r * sin(angle)
			val y = base.y + (0.15 + rng.nextDouble() * 0.85) * h + 0.05
			val noteColor = rng.nextDouble() * 0.98
			sendNoteParticleLongDistance(level, x, y, z, noteColor)
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
