package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Lapras overworld defense: Cobblemon Water Pulse–style projectiles (Snowstorm VFX + move sounds).
 * - [RANGED_MIN_DISTANCE]–[RANGED_MAX_DISTANCE]: volley while Cobblemon has a combat target (tick).
 * - Melee hook: [doHurtTarget] mixin still replaces point-blank hits (shared cooldown).
 */
object LaprasProjectileCombat {
	/**
	 * Minimum ticks between volleys. Lapras ground `special` attack anim is 3s (60 ticks); spacing
	 * attacks at least that far lets each shot play the full pose instead of restarting mid-anim.
	 */
	private const val COOLDOWN_TICKS = 60
	private const val PROJECTILE_SPEED = 3.4f
	private const val INACCURACY = 3.5f
	private const val PULSE_COUNT = 2

	/** Blocks from Cobblemon eye anchor forward along the shot toward the snout/mouth. */
	private const val MOUTH_OFFSET_ALONG_SHOT = 0.9

	private val ACTOR_SOUND_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon", "move.waterpulse.actor")
	private val ACTOR_SUDS_EFFECT: ResourceLocation = ResourceLocation.parse("cobblemon:waterpulse_actorsuds")
	private val ACTOR_ENTITY_EFFECT: ResourceLocation = ResourceLocation.parse("cobblemon:waterpulse_actor")

	private val BEAM_SOURCE_LOCATORS: List<String> = listOf("special", "target")
	private val BEAM_TARGET_LOCATORS: List<String> = listOf("target")

	private const val WORLD_PULSE_RANGE = 128.0

	/** Below this, only the melee ([doHurtTarget]) path typically runs; above [RANGED_MAX_DISTANCE], no shot. */
	const val RANGED_MIN_DISTANCE = 4.0
	const val RANGED_MAX_DISTANCE = 30.0

	private val lastShotGameTick = ConcurrentHashMap<UUID, Int>()

	@JvmStatic
	fun tickRangedVolley(entity: PokemonEntity) {
		if (entity.level().isClientSide) return
		if (!isLaprasProjectileContext(entity)) return

		val level = entity.level() as? ServerLevel ?: return
		val target = entity.resolveCombatTarget() ?: return
		if (!target.isAlive) return
		if (!entity.hasLineOfSight(target)) return

		val dist = entity.distanceToTargetHoriz(target)
		if (dist < RANGED_MIN_DISTANCE || dist > RANGED_MAX_DISTANCE) return

		if (!tryConsumeCooldown(entity, level)) return

		val perPulseDamage = (0.65 + entity.pokemon.level * 0.12).coerceIn(0.65, 2.75)
		fireVolley(level, entity, target, perPulseDamage)
	}

	@JvmStatic
	fun tryReplaceMeleeWithProjectile(attacker: PokemonEntity, target: Entity): Boolean {
		if (attacker.level().isClientSide) return false
		if (target !is LivingEntity || !target.isAlive) return false
		if (!isLaprasProjectileContext(attacker)) return false

		val level = attacker.level() as? ServerLevel ?: return false
		if (!tryConsumeCooldown(attacker, level)) return true

		val perPulseDamage = (0.65 + attacker.pokemon.level * 0.12).coerceIn(0.65, 2.75)
		fireVolley(level, attacker, target, perPulseDamage)
		return true
	}

	private fun isLaprasProjectileContext(entity: PokemonEntity): Boolean {
		if (entity.beamMode != 0) return false
		if (entity.ownerUUID == null) return false
		if (entity.level().dimension() != Level.OVERWORLD) return false
		if (entity.pokemon.species.resourceIdentifier.path != "lapras") return false
		return true
	}

	private fun PokemonEntity.resolveCombatTarget(): LivingEntity? {
		target?.let { if (it.isAlive) return it }
		return brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)?.takeIf { it.isAlive }
	}

	private fun PokemonEntity.distanceToTargetHoriz(target: LivingEntity): Double {
		val dx = x - target.x
		val dz = z - target.z
		return sqrt(dx * dx + dz * dz)
	}

	/** Returns true if an attack (volley) may proceed; false if still on cooldown (caller should skip firing). */
	private fun tryConsumeCooldown(attacker: PokemonEntity, level: ServerLevel): Boolean {
		val now = level.server.tickCount
		val uuid = attacker.uuid
		val last = lastShotGameTick[uuid] ?: -COOLDOWN_TICKS * 2
		if (now - last < COOLDOWN_TICKS) return false
		lastShotGameTick[uuid] = now
		return true
	}

	private fun fireVolley(
		level: ServerLevel,
		attacker: PokemonEntity,
		target: LivingEntity,
		perPulseDamage: Double,
	) {
		orientPokemonTowardTarget(attacker, target)
		// Cobblemon poser: battle poses map `special` → lapras `special` / surfacewater_special / water_special.
		attacker.playAnimation("special", listOf())

		val eye = attacker.getEyePosition(1f)
		val targetEye = target.getEyePosition(1f)
		val toTarget = targetEye.subtract(eye)
		val lenSq = toTarget.lengthSqr()
		if (lenSq < 1.0e-6) return
		val towardMouth = toTarget.normalize()
		val spawnOrigin = eye.add(towardMouth.scale(MOUTH_OFFSET_ALONG_SHOT))
		val aim = targetEye.subtract(spawnOrigin).normalize()

		playCobblemonSound(level, spawnOrigin, ACTOR_SOUND_ID)

		sendEntitySnowstormAround(
			level,
			SpawnSnowstormEntityParticlePacket(
				ACTOR_SUDS_EFFECT,
				attacker.id,
				listOf("root"),
				null,
				emptyList(),
			),
			attacker.x,
			attacker.y,
			attacker.z,
		)
		sendEntitySnowstormAround(
			level,
			SpawnSnowstormEntityParticlePacket(
				ACTOR_ENTITY_EFFECT,
				attacker.id,
				BEAM_SOURCE_LOCATORS,
				target.id,
				BEAM_TARGET_LOCATORS,
			),
			attacker.x,
			attacker.y,
			attacker.z,
		)

		for (i in 0 until PULSE_COUNT) {
			fireOne(level, attacker, target, spawnOrigin, targetEye, aim, perPulseDamage, i)
		}
	}

	private fun fireOne(
		level: ServerLevel,
		shooter: PokemonEntity,
		target: LivingEntity,
		origin: Vec3,
		targetEye: Vec3,
		dir: Vec3,
		damage: Double,
		index: Int,
	) {
		val pulse = WaterPulseProjectile(BetterLaprasEntities.WATER_PULSE, level)
		pulse.setOwner(shooter)
		pulse.setBeamTarget(target)
		pulse.setPulseDamage(damage)
		val delayTicks = WaterPulseProjectile.computeImpactDelayTicks(origin, targetEye, PROJECTILE_SPEED)
		pulse.setScheduledImpact(level.server.tickCount + delayTicks, target.id)
		pulse.moveTo(origin.x, origin.y, origin.z, shooter.yRot, shooter.xRot)

		val spread = (index - PULSE_COUNT / 2) * 0.04
		val vx = dir.x + spread * 0.15
		val vy = dir.y + spread * 0.08
		val vz = dir.z + spread * 0.15
		val vec = Vec3(vx, vy, vz).normalize()

		pulse.shoot(vec.x, vec.y, vec.z, PROJECTILE_SPEED, INACCURACY)
		level.addFreshEntity(pulse)
	}

	/** Snap Lapras body/head yaw so the model faces the combat target when firing. */
	private fun orientPokemonTowardTarget(pokemon: PokemonEntity, target: LivingEntity) {
		val eye = pokemon.getEyePosition(1f)
		val targetEye = target.getEyePosition(1f)
		val dx = targetEye.x - eye.x
		val dz = targetEye.z - eye.z
		val yRot = Mth.wrapDegrees((Mth.atan2(dz, dx) * Mth.RAD_TO_DEG).toFloat() - 90.0f)

		pokemon.setYRot(yRot)
		pokemon.yRotO = yRot
		pokemon.yHeadRot = yRot
		pokemon.yHeadRotO = yRot
		pokemon.yBodyRot = yRot
		pokemon.yBodyRotO = yRot
	}

	private fun sendEntitySnowstormAround(
		level: ServerLevel,
		packet: SpawnSnowstormEntityParticlePacket,
		ax: Double,
		ay: Double,
		az: Double,
	) {
		packet.sendToPlayersAround(ax, ay, az, WORLD_PULSE_RANGE, level.dimension()) { false }
	}

	private fun playCobblemonSound(level: ServerLevel, at: Vec3, soundId: ResourceLocation) {
		val soundEvent: SoundEvent? = BuiltInRegistries.SOUND_EVENT.get(soundId)
		if (soundEvent != null) {
			level.playSound(null, at.x, at.y, at.z, soundEvent, SoundSource.NEUTRAL, 0.85f, 1.0f)
		}
	}
}
