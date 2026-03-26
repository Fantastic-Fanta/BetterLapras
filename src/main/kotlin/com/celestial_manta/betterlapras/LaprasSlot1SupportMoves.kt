package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.pokemon.status.statuses.persistent.SleepStatus
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Slot-1 status moves: periodic Sing (AoE sleep / slowness) or Mist-family (cleanse + absorption on owner).
 * When active, [suppressesCombat] is true so Lapras does not use BetterLapras ranged or melee strike replacements.
 */
object LaprasSlot1SupportMoves {
	/** Sing and Mist-family pulse interval (40 s at 20 tps). */
	private const val SUPPORT_COOLDOWN_TICKS = 40 * 20
	private const val SING_RADIUS = 12.0
	private const val PARTICLE_RANGE = 128.0

	private val mistFamilyTemplateNames = setOf(
		"mist",
		"safeguard",
		"haze",
		"auroraveil",
		"aurora_veil",
	)

	private val lastSupportGameTick = ConcurrentHashMap<UUID, Int>()

	private enum class SupportKind {
		SING,
		MIST_FAMILY,
	}

	@JvmStatic
	fun tickSlot1Support(entity: PokemonEntity) {
		if (entity.level().isClientSide) return
		if (!isLaprasSupportContext(entity)) return
		val kind = slot1Kind(entity) ?: return
		val level = entity.level() as? ServerLevel ?: return
		if (kind == SupportKind.MIST_FAMILY) {
			val owner = entity.ownerUUID?.let { level.server.playerList.getPlayer(it) } ?: return
			if (!tryConsumeSupportCooldown(entity, level)) return
			activateMistFamily(entity, level, owner)
		} else {
			if (!tryConsumeSupportCooldown(entity, level)) return
			activateSing(entity, level)
		}
	}

	@JvmStatic
	fun suppressesCombat(entity: PokemonEntity): Boolean {
		if (!isLaprasSupportContext(entity)) return false
		return slot1Kind(entity) != null
	}

	private fun isLaprasSupportContext(entity: PokemonEntity): Boolean {
		if (entity.beamMode != 0) return false
		if (entity.ownerUUID == null) return false
		if (entity.level().dimension() != Level.OVERWORLD) return false
		if (entity.pokemon.species.resourceIdentifier.path != "lapras") return false
		return true
	}

	private fun slot1Kind(entity: PokemonEntity): SupportKind? {
		val move = entity.pokemon.moveSet.getMovesWithNulls().getOrNull(0) ?: return null
		val name = move.template.name.lowercase()
		if (name == "sing") return SupportKind.SING
		if (name in mistFamilyTemplateNames) return SupportKind.MIST_FAMILY
		return null
	}

	private fun tryConsumeSupportCooldown(entity: PokemonEntity, level: ServerLevel): Boolean {
		val now = level.server.tickCount
		val uuid = entity.uuid
		val last = lastSupportGameTick[uuid] ?: -SUPPORT_COOLDOWN_TICKS * 2
		if (now - last < SUPPORT_COOLDOWN_TICKS) return false
		lastSupportGameTick[uuid] = now
		return true
	}

	private fun activateSing(entity: PokemonEntity, level: ServerLevel) {
		LaprasPosableAnimationPackets.broadcastLaprasSing(entity)
		playSound(level, entity.position(), "move.sing.actor")
		val singLocators = listOf("mouth", "jaw_lower", "special", "target")
		broadcastSnowstorm(
			level,
			SpawnSnowstormEntityParticlePacket(
				ResourceLocation.parse("cobblemon:sing_actorwave"),
				entity.id,
				singLocators,
				null,
				emptyList(),
			),
			entity.x,
			entity.y,
			entity.z,
		)
		broadcastSnowstorm(
			level,
			SpawnSnowstormEntityParticlePacket(
				ResourceLocation.parse("cobblemon:sing_actor"),
				entity.id,
				singLocators,
				null,
				emptyList(),
			),
			entity.x,
			entity.y,
			entity.z,
		)
		val ownerId = entity.ownerUUID
		val box = entity.boundingBox.inflate(SING_RADIUS)
		for (e in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (e == entity) continue
			if (!e.isAlive) continue
			if (e.uuid == ownerId) continue
			if (distance(entity, e) > SING_RADIUS) continue
			when (e) {
				is PokemonEntity -> {
					if (ownerId != null && e.ownerUUID != null && e.ownerUUID == ownerId) continue
					e.pokemon.applyStatus(SleepStatus())
				}
				is Monster -> {
					e.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 2, false, true, true))
				}
			}
		}
	}

	private fun distance(a: LivingEntity, b: LivingEntity): Double {
		val dx = a.x - b.x
		val dy = a.y - b.y
		val dz = a.z - b.z
		return sqrt(dx * dx + dy * dy + dz * dz)
	}

	private fun activateMistFamily(entity: PokemonEntity, level: ServerLevel, owner: ServerPlayer) {
		playSound(level, entity.position(), "move.mist.actor")
		broadcastSnowstorm(
			level,
			SpawnSnowstormEntityParticlePacket(
				ResourceLocation.parse("cobblemon:mist_actor"),
				entity.id,
				listOf("middle"),
				null,
				emptyList(),
			),
			entity.x,
			entity.y,
			entity.z,
		)
		broadcastSnowstorm(
			level,
			SpawnSnowstormEntityParticlePacket(
				ResourceLocation.parse("cobblemon:mist_actorshroud"),
				entity.id,
				listOf("root"),
				null,
				emptyList(),
			),
			entity.x,
			entity.y,
			entity.z,
		)
		clearHarmfulVanillaEffects(owner)
		owner.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 25 * 20, 1, false, true, true))
	}

	private fun clearHarmfulVanillaEffects(player: ServerPlayer) {
		for (inst in player.activeEffects.toList()) {
			if (!inst.effect.value().isBeneficial()) {
				player.removeEffect(inst.effect)
			}
		}
	}

	private fun broadcastSnowstorm(
		level: ServerLevel,
		packet: SpawnSnowstormEntityParticlePacket,
		x: Double,
		y: Double,
		z: Double,
	) {
		packet.sendToPlayersAround(x, y, z, PARTICLE_RANGE, level.dimension()) { false }
	}

	private fun playSound(level: ServerLevel, at: Vec3, cobblemonPath: String) {
		val id = ResourceLocation.fromNamespaceAndPath("cobblemon", cobblemonPath)
		val soundEvent: SoundEvent? = BuiltInRegistries.SOUND_EVENT.get(id)
		if (soundEvent != null) {
			level.playSound(null, at.x, at.y, at.z, soundEvent, SoundSource.NEUTRAL, 0.85f, 1.0f)
		}
	}
}
