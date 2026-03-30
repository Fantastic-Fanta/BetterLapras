package com.celestial_manta.betterlapras.lapras.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.celestial_manta.betterlapras.BetterLaprasEntities
import com.celestial_manta.betterlapras.lapras.moves.effects.LaprasIceBeamEffects
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasMoveDamageFormulas
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasMoveProjectile
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasMoveShotProfiles
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasPulseKind
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasShotProfile
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


object LaprasMoveCombat {
	private const val COOLDOWN_TICKS = 60
	private const val ICE_SHARD_COOLDOWN_TICKS = COOLDOWN_TICKS / 3
	private const val PROJECTILES_PER_VOLLEY = 2
	private const val MOUTH_OFFSET_ALONG_SHOT = 0.9
	const val RANGED_DISTANCE_HINT_MIN_BLOCKS = 1.0
	const val RANGED_DISTANCE_HINT_MAX_BLOCKS = 60.0

	private val lastShotGameTick = ConcurrentHashMap<UUID, Int>()

	@JvmStatic
	fun tickRangedVolley(entity: PokemonEntity) {
		if (entity.level().isClientSide) return
		if (!isLaprasMoveStrikeContext(entity)) return
		if (LaprasSlot1SupportMoves.suppressesCombat(entity)) return

		val level = entity.level() as? ServerLevel ?: return
		val target = entity.resolveCombatTarget() ?: return
		if (!target.isAlive) return
		if (!entity.hasLineOfSight(target)) return

		val profile = LaprasMoveShotProfiles.resolveShotProfile(entity) ?: return
		val dist = entity.distanceToTargetHoriz(target)
		if (dist < profile.rangedMinBlocks || dist > profile.rangedMaxBlocks) return
		if (!tryConsumeCooldown(entity, level, profile.kind)) return

		val strikeDamage = LaprasMoveDamageFormulas.compute(profile.kind, entity.pokemon.level)
		fireVolley(level, entity, target, strikeDamage, profile)
	}

	@JvmStatic
	fun tryReplaceMeleeWithProjectile(attacker: PokemonEntity, target: Entity): Boolean {
		if (attacker.level().isClientSide) return false
		if (target !is LivingEntity || !target.isAlive) return false
		if (!isLaprasMoveStrikeContext(attacker)) return false
		if (LaprasSlot1SupportMoves.suppressesCombat(attacker)) return false

		val profile = LaprasMoveShotProfiles.resolveShotProfile(attacker) ?: return false

		val level = attacker.level() as? ServerLevel ?: return false
		if (!tryConsumeCooldown(attacker, level, profile.kind)) return true

		val strikeDamage = LaprasMoveDamageFormulas.compute(profile.kind, attacker.pokemon.level)
		fireVolley(level, attacker, target, strikeDamage, profile)
		return true
	}

	private fun isLaprasMoveStrikeContext(entity: PokemonEntity): Boolean {
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

	private fun cooldownTicksFor(kind: LaprasPulseKind): Int =
		if (kind == LaprasPulseKind.ICE_SHARD) ICE_SHARD_COOLDOWN_TICKS else COOLDOWN_TICKS

	private fun tryConsumeCooldown(attacker: PokemonEntity, level: ServerLevel, kind: LaprasPulseKind): Boolean {
		val now = level.server.tickCount
		val uuid = attacker.uuid
		val required = cooldownTicksFor(kind)
		val last = lastShotGameTick[uuid] ?: -required * 2
		if (now - last < required) return false
		lastShotGameTick[uuid] = now
		return true
	}

	private fun fireVolley(
		level: ServerLevel,
		attacker: PokemonEntity,
		target: LivingEntity,
		strikeDamage: Double,
		profile: LaprasShotProfile,
	) {
		orientPokemonTowardTarget(attacker, target)

		val eye = attacker.getEyePosition(1f)
		val targetEye = target.betterLaprasStrikeTargetPoint(1f)
		val toTarget = targetEye.subtract(eye)
		val lenSq = toTarget.lengthSqr()
		if (lenSq < 1.0e-6) return
		val towardMouth = toTarget.normalize()
		val spawnOrigin = eye.add(towardMouth.scale(MOUTH_OFFSET_ALONG_SHOT))
		val aim = targetEye.subtract(spawnOrigin).normalize()

		if (profile.kind == LaprasPulseKind.ICE_BEAM_MOVE) {
			val delayTicks = LaprasMoveProjectile.computeImpactDelayTicks(
				spawnOrigin,
				targetEye,
				profile.projectileSpeed,
				profile.impactPadTicks,
			)
			LaprasIceBeamEffects.applyPreBeamFreeze(target, delayTicks, attacker)
		}

		attacker.playAnimation("special", listOf())

		val pres = profile.presentation
		playCobblemonSound(
			level,
			spawnOrigin,
			ResourceLocation.fromNamespaceAndPath("cobblemon", pres.actorSoundPath),
		)

		repeat(PROJECTILES_PER_VOLLEY) {
			spawnMoveProjectile(level, attacker, target, spawnOrigin, targetEye, aim, strikeDamage, profile)
		}
	}

	private fun spawnMoveProjectile(
		level: ServerLevel,
		shooter: PokemonEntity,
		target: LivingEntity,
		origin: Vec3,
		targetEye: Vec3,
		dir: Vec3,
		strikeDamage: Double,
		profile: LaprasShotProfile,
	) {
		val projectile = LaprasMoveProjectile(BetterLaprasEntities.LAPRAS_MOVE_PROJECTILE, level)
		projectile.setOwner(shooter)
		projectile.setBeamTarget(target)
		projectile.setStrikeDamage(strikeDamage)
		projectile.applyMoveProfile(profile.kind)
		val speed = profile.projectileSpeed
		val delayTicks = LaprasMoveProjectile.computeImpactDelayTicks(
			origin,
			targetEye,
			speed,
			profile.impactPadTicks,
		)
		projectile.setScheduledImpact(level.server.tickCount + delayTicks, target.id)
		projectile.moveTo(origin.x, origin.y, origin.z, shooter.yRot, shooter.xRot)
		if (profile.kind == LaprasPulseKind.SHEER_COLD) {
			projectile.setSheerColdCone(origin, targetEye)
		}

		projectile.shoot(dir.x, dir.y, dir.z, speed, profile.inaccuracy)
		level.addFreshEntity(projectile)
	}

	private fun orientPokemonTowardTarget(pokemon: PokemonEntity, target: LivingEntity) {
		val eye = pokemon.getEyePosition(1f)
		val targetEye = target.betterLaprasStrikeTargetPoint(1f)
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
