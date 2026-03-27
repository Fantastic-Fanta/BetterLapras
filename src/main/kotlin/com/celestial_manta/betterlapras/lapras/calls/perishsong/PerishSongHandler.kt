package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat: same message must contain "Lapras" and "Perish song" (case-insensitive, loose wording), 20 min
 * cooldown per player; on cooldown the player sees an action bar message from Lapras.
 * That snapshots a center and marks every [LivingEntity] within [PerishSongConfig.MARK_RADIUS] once (no one who enters later is added): [ServerPlayer]s, vanilla [net.minecraft.world.entity.MobCategory.MONSTER]
 * mobs, and types in tag [PerishSongConfig.HOSTILE_ENTITY_TAG] — never Cobblemon Pokémon. Marked targets get glowing + Slowness II;
 * players in that initial set hear the custom sting. Leaving range removes them from the
 * mark list and strips glowing, slowness, darkness, and nausea. At each of the first three milestones
 * (0s / 10s / 20s), marked targets still in range get a darkness pulse and the focal Lapras cry
 * animation; from 20s marked targets get
 * stronger nausea, then after 5s stronger still until 30s; remaining marked entities die. Focal Lapras
 * is never marked. While the song runs, end rod particles form a horizontal ring around the focal
 * Lapras with end-rod rings; floaty music notes appear occasionally nearby (stationary, forced).
 * When a marked target dies (including resolve), an end rod burst plays at its position.
 */
object PerishSongHandler {
	private val sessions = Collections.synchronizedList(mutableListOf<PerishSongSession>())
	private val lastTriggerGameTick = ConcurrentHashMap<UUID, Int>()

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
						PerishSongParticles.spawnEndRodBurst(level, pos)
						break
					}
				}
			}
		}
	}

	private fun onChat(sender: ServerPlayer, message: PlayerChatMessage) {
		val plain = PerishSongChat.normalizePhrase(message.decoratedContent().string)
		if (!PerishSongChat.matchesPerishSongChat(plain)) return

		val level = sender.serverLevel()
		val now = level.server.tickCount
		if (PerishSongChat.isOnCooldown(now, lastTriggerGameTick[sender.uuid])) {
			PerishSongChat.sendNoEnergyMessage(sender)
			return
		}

		val lapras = PerishSongTargeting.findNearestOwnedLapras(sender) ?: return
		val center = lapras.position()
		val marked = PerishSongTargeting.collectMarkedLiving(level, center, lapras.uuid)

		for (uuid in marked) {
			val entity = level.getEntity(uuid) as? LivingEntity ?: continue
			PerishSongMarkEffects.applyBaseMarkEffects(entity)
		}
		PerishSongAudio.playStingForMarkedPlayers(level, center, marked)

		lastTriggerGameTick[sender.uuid] = now
		synchronized(sessions) {
			sessions.add(PerishSongSession(level, center, marked, now, lapras.uuid))
		}
	}

	private fun tickWorld(level: ServerLevel) {
		synchronized(sessions) {
			PerishSongFocalAnchor.anchorFocalLapras(sessions, level)
		}
		tickPerishSongParticles(level)
		val tick = level.server.tickCount
		synchronized(sessions) {
			val iter = sessions.iterator()
			while (iter.hasNext()) {
				val session = iter.next()
				if (session.level !== level) continue
				val elapsed = tick - session.startTick
				tickMarkedEffects(level, session, elapsed)
				while (session.nextMilestoneIndex < PerishSongConfig.eventOffsetsTicks.size &&
					elapsed >= PerishSongConfig.eventOffsetsTicks[session.nextMilestoneIndex]
				) {
					val step = PerishSongMilestones.STEPS.getOrNull(session.nextMilestoneIndex)
					step?.run(level, session)
					session.nextMilestoneIndex++
				}
				if (session.nextMilestoneIndex >= PerishSongConfig.eventOffsetsTicks.size) {
					iter.remove()
				}
			}
		}
	}

	private fun tickPerishSongParticles(level: ServerLevel) {
		val t = level.gameTime.toDouble()
		val tick = level.server.tickCount
		synchronized(sessions) {
			for (session in sessions) {
				if (session.level !== level) continue
				PerishSongParticles.tickSessionParticles(level, session, t, tick)
			}
		}
	}

	private fun tickMarkedEffects(level: ServerLevel, session: PerishSongSession, elapsed: Int) {
		val center = session.center
		val markIt = session.marked.iterator()
		while (markIt.hasNext()) {
			val uuid = markIt.next()
			val entity = level.getEntity(uuid) as? LivingEntity
			if (entity == null || !entity.isAlive) {
				markIt.remove()
				continue
			}
			if (entity.position().distanceToSqr(center) > PerishSongConfig.markRadiusSq) {
				PerishSongMarkEffects.clearPerishSongEffects(entity)
				markIt.remove()
				continue
			}
			PerishSongMarkEffects.applyBaseMarkEffects(entity)
			for (layer in PerishSongTickLayers.layers) {
				layer.onStillMarked(level, session, entity, elapsed)
			}
		}
	}
}
