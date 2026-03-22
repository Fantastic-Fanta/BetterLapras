package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
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
	/** 3 seconds between volleys (20 tps → 60 ticks). */
	private const val COOLDOWN_TICKS = 60

	private const val PULSE_COUNT = 1

	/** Applied after base damage from level ([BASE_PULSE_DAMAGE_MIN]–[BASE_PULSE_DAMAGE_MAX]). */
	private const val PULSE_DAMAGE_MULTIPLIER = 3.0

	private const val BASE_PULSE_DAMAGE_MIN = 0.65
	private const val BASE_PULSE_DAMAGE_PER_LEVEL = 0.2
	private const val BASE_PULSE_DAMAGE_MAX = 2.75

	/** Blocks from Cobblemon eye anchor forward along the shot toward the snout/mouth. */
	private const val MOUTH_OFFSET_ALONG_SHOT = 0.9

	/** Below this, only the melee ([doHurtTarget]) path typically runs; above [RANGED_MAX_DISTANCE], no shot. */
	const val RANGED_MIN_DISTANCE = 4.0
	const val RANGED_MAX_DISTANCE = 50.0

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

		val profile = LaprasMoveShotProfiles.resolveShotProfile(entity) ?: return
		if (!tryConsumeCooldown(entity, level)) return

		val perPulseDamage = pulseDamageForLevel(entity.pokemon.level) * profile.damageMultiplier
		fireVolley(level, entity, target, perPulseDamage, profile)
	}

	@JvmStatic
	fun tryReplaceMeleeWithProjectile(attacker: PokemonEntity, target: Entity): Boolean {
		if (attacker.level().isClientSide) return false
		if (target !is LivingEntity || !target.isAlive) return false
		if (!isLaprasProjectileContext(attacker)) return false

		val profile = LaprasMoveShotProfiles.resolveShotProfile(attacker) ?: return false

		val level = attacker.level() as? ServerLevel ?: return false
		if (!tryConsumeCooldown(attacker, level)) return true

		val perPulseDamage = pulseDamageForLevel(attacker.pokemon.level) * profile.damageMultiplier
		fireVolley(level, attacker, target, perPulseDamage, profile)
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
		profile: LaprasShotProfile,
	) {
		orientPokemonTowardTarget(attacker, target)

		val eye = attacker.getEyePosition(1f)
		val targetEye = target.getEyePosition(1f)
		val toTarget = targetEye.subtract(eye)
		val lenSq = toTarget.lengthSqr()
		if (lenSq < 1.0e-6) return
		val towardMouth = toTarget.normalize()
		val spawnOrigin = eye.add(towardMouth.scale(MOUTH_OFFSET_ALONG_SHOT))
		val aim = targetEye.subtract(spawnOrigin).normalize()

		if (profile.kind == LaprasPulseKind.ICE_BEAM && attacker.slot1MoveIsIceType()) {
			val delayTicks = WaterPulseProjectile.computeImpactDelayTicks(
				spawnOrigin,
				targetEye,
				profile.projectileSpeed,
				profile.impactPadTicks,
			)
			LaprasIceBeamEffects.applyPreBeamFreeze(target, delayTicks)
		}

		// Cobblemon poser: battle poses map `special` → lapras `special` / surfacewater_special / water_special.
		attacker.playAnimation("special", listOf())

		val pres = profile.presentation
		playCobblemonSound(
			level,
			spawnOrigin,
			ResourceLocation.fromNamespaceAndPath("cobblemon", pres.actorSoundPath),
		)

		// Beam VFX: one packet from [WaterPulseProjectile] on first tick only (was doubled here + every N ticks in-flight).

		repeat(PULSE_COUNT) {
			fireOne(level, attacker, target, spawnOrigin, targetEye, aim, perPulseDamage, profile)
		}
	}

	private fun pulseDamageForLevel(level: Int): Double {
		val base = (BASE_PULSE_DAMAGE_MIN + level * BASE_PULSE_DAMAGE_PER_LEVEL)
			.coerceIn(BASE_PULSE_DAMAGE_MIN, BASE_PULSE_DAMAGE_MAX)
		return base * PULSE_DAMAGE_MULTIPLIER
	}

	private fun fireOne(
		level: ServerLevel,
		shooter: PokemonEntity,
		target: LivingEntity,
		origin: Vec3,
		targetEye: Vec3,
		dir: Vec3,
		damage: Double,
		profile: LaprasShotProfile,
	) {
		val pulse = WaterPulseProjectile(BetterLaprasEntities.WATER_PULSE, level)
		pulse.setOwner(shooter)
		pulse.setBeamTarget(target)
		pulse.setPulseDamage(damage)
		pulse.setPulseStyle(profile.kind)
		val speed = profile.projectileSpeed
		val delayTicks = WaterPulseProjectile.computeImpactDelayTicks(
			origin,
			targetEye,
			speed,
			profile.impactPadTicks,
		)
		pulse.setScheduledImpact(level.server.tickCount + delayTicks, target.id)
		pulse.moveTo(origin.x, origin.y, origin.z, shooter.yRot, shooter.xRot)

		pulse.shoot(dir.x, dir.y, dir.z, speed, profile.inaccuracy)
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

	private fun playCobblemonSound(level: ServerLevel, at: Vec3, soundId: ResourceLocation) {
		val soundEvent: SoundEvent? = BuiltInRegistries.SOUND_EVENT.get(soundId)
		if (soundEvent != null) {
			level.playSound(null, at.x, at.y, at.z, soundEvent, SoundSource.NEUTRAL, 0.85f, 1.0f)
		}
	}
}
