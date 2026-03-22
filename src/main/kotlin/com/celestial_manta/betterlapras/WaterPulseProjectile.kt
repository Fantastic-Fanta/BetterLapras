package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.resources.ResourceLocation
import kotlin.math.ceil

/**
 * Travelling pulse: **one** Cobblemon Lapras→target beam packet per shot ([PulsePresentation.trailPulseEffect]),
 * sent on the first tick the owner + target resolve; hit uses a **single** world Snowstorm (no collision damage).
 */
class WaterPulseProjectile(
	type: EntityType<out WaterPulseProjectile>,
	level: Level,
) : Projectile(type, level) {

	var pulseDamage: Double = 1.0
		private set

	private var pulseKind: LaprasPulseKind = LaprasPulseKind.WATER_DEFAULT
	private var presentation: PulsePresentation =
		LaprasMoveShotProfiles.presentationForKind(LaprasPulseKind.WATER_DEFAULT)

	/** True after the one-shot entity-linked beam VFX has been sent (persisted so reloads don’t re-fire). */
	private var trailPulseEmitted: Boolean = false

	/** Network id of the combat target; used for entity-linked trail packets. */
	private var beamTargetId: Int = 0

	/** Server tick ([net.minecraft.server.MinecraftServer.tickCount]) when [deliverScheduledImpact] runs. */
	private var impactServerTick: Int = -1
	private var scheduledVictimEntityId: Int = 0
	private var impactDelivered: Boolean = false
	private var impactCancelled: Boolean = false

	fun setPulseDamage(value: Double) {
		pulseDamage = value
	}

	/** Visuals + hit sounds; persisted via [pulseKind] in NBT after chunk reload. */
	fun setPulseStyle(kind: LaprasPulseKind) {
		pulseKind = kind
		presentation = LaprasMoveShotProfiles.presentationForKind(kind)
	}

	fun setBeamTarget(target: Entity) {
		beamTargetId = target.id
	}

	fun setScheduledImpact(impactTick: Int, victimEntityId: Int) {
		impactServerTick = impactTick
		scheduledVictimEntityId = victimEntityId
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

	override fun addAdditionalSaveData(tag: CompoundTag) {
		super.addAdditionalSaveData(tag)
		tag.putDouble("PulseDamage", pulseDamage)
		tag.putInt("BetterLaprasPulseKind", pulseKind.ordinal)
		tag.putInt("BetterLaprasImpactTick", impactServerTick)
		tag.putInt("BetterLaprasVictimId", scheduledVictimEntityId)
		tag.putBoolean("BetterLaprasImpactDelivered", impactDelivered)
		tag.putBoolean("BetterLaprasImpactCancelled", impactCancelled)
		tag.putInt("BetterLaprasBeamTarget", beamTargetId)
		tag.putBoolean("BetterLaprasTrailPulseEmitted", trailPulseEmitted)
	}

	override fun readAdditionalSaveData(tag: CompoundTag) {
		super.readAdditionalSaveData(tag)
		if (tag.contains("PulseDamage")) {
			pulseDamage = tag.getDouble("PulseDamage")
		}
		if (tag.contains("BetterLaprasPulseKind")) {
			setPulseStyle(LaprasPulseKind.fromOrdinal(tag.getInt("BetterLaprasPulseKind")))
		}
		if (tag.contains("BetterLaprasImpactTick")) {
			impactServerTick = tag.getInt("BetterLaprasImpactTick")
		}
		if (tag.contains("BetterLaprasVictimId")) {
			scheduledVictimEntityId = tag.getInt("BetterLaprasVictimId")
		}
		if (tag.contains("BetterLaprasImpactDelivered")) {
			impactDelivered = tag.getBoolean("BetterLaprasImpactDelivered")
		}
		if (tag.contains("BetterLaprasImpactCancelled")) {
			impactCancelled = tag.getBoolean("BetterLaprasImpactCancelled")
		}
		if (tag.contains("BetterLaprasBeamTarget")) {
			beamTargetId = tag.getInt("BetterLaprasBeamTarget")
		}
		if (tag.contains("BetterLaprasTrailPulseEmitted")) {
			trailPulseEmitted = tag.getBoolean("BetterLaprasTrailPulseEmitted")
		}
	}

	override fun getDefaultGravity(): Double = 0.02

	override fun canHitEntity(entity: Entity): Boolean {
		if (!super.canHitEntity(entity)) return false
		val owner = owner
		if (owner != null && entity == owner) return false
		if (impactServerTick >= 0 && !impactCancelled) return false
		return true
	}

	override fun tick() {
		val serverLevel = if (!level().isClientSide && level() is ServerLevel) level() as ServerLevel else null
		val skipHitsUntilScheduledImpact = serverLevel != null &&
			impactServerTick >= 0 &&
			!impactDelivered &&
			!impactCancelled &&
			serverLevel.server.tickCount < impactServerTick

		if (serverLevel != null && !impactDelivered && !impactCancelled && impactServerTick >= 0) {
			if (serverLevel.server.tickCount >= impactServerTick) {
				deliverScheduledImpact(serverLevel)
				return
			}
		}

		super.tick()
		val vec3 = deltaMovement
		if (!skipHitsUntilScheduledImpact) {
			val hitResult = ProjectileUtil.getHitResultOnMoveVector(this) { canHitEntity(it) }
			hitTargetOrDeflectSelf(hitResult)
			if (isRemoved) return
		}

		val x = this.x + vec3.x
		val y = this.y + vec3.y
		val z = this.z + vec3.z
		updateRotation()

		if (!skipHitsUntilScheduledImpact &&
			level().getBlockStates(boundingBox).noneMatch { obj: BlockBehaviour.BlockStateBase -> obj.isAir }
		) {
			if (pulseKind == LaprasPulseKind.ICE_BEAM) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(level().getEntity(beamTargetId) as? LivingEntity)
			}
			discard()
			return
		}

		setDeltaMovement(vec3.scale(0.99))
		applyGravity()
		setPos(x, y, z)

		if (!level().isClientSide && level() is ServerLevel && !trailPulseEmitted) {
			when {
				tryEmitTrailPulseOnce() -> trailPulseEmitted = true
				beamTargetLostOrDead() -> trailPulseEmitted = true
			}
		}
	}

	private fun iceSlownessAppliesNow(): Boolean {
		if (pulseKind != LaprasPulseKind.ICE_BEAM) return false
		return (owner as? PokemonEntity)?.slot1MoveIsIceType() == true
	}

	private fun beamTargetLostOrDead(): Boolean {
		if (beamTargetId == 0) return false
		val t = level().getEntity(beamTargetId) ?: return true
		return t !is LivingEntity || !t.isAlive
	}

	private fun spawnSnowstormWorld(effect: ResourceLocation, pos: Vec3, range: Double = WORLD_PARTICLE_RANGE) {
		SpawnSnowstormParticlePacket(effect, pos).sendToPlayersAround(
			pos.x,
			pos.y,
			pos.z,
			range,
			level().dimension(),
		) { false }
	}

	private fun broadcastEntitySnowstorm(packet: SpawnSnowstormEntityParticlePacket, ax: Double, ay: Double, az: Double) {
		val sl = level() as ServerLevel
		packet.sendToPlayersAround(ax, ay, az, WORLD_PARTICLE_RANGE, sl.dimension()) { false }
	}

	/** One-shot Cobblemon beam (Lapras special/target → target). Returns false if owner/target not ready yet. */
	private fun tryEmitTrailPulseOnce(): Boolean {
		val lapras = owner as? PokemonEntity ?: return false
		if (beamTargetId == 0) return false
		val tgt = level().getEntity(beamTargetId) ?: return false
		if (!tgt.isAlive || tgt !is LivingEntity) return false
		broadcastEntitySnowstorm(
			SpawnSnowstormEntityParticlePacket(
				presentation.trailPulseEffect,
				lapras.id,
				SOURCE_LOCATORS_BEAM,
				tgt.id,
				TARGET_LOCATORS_BEAM,
			),
			lapras.x,
			lapras.y,
			lapras.z,
		)
		return true
	}

	override fun onHitEntity(entityHitResult: EntityHitResult) {
		super.onHitEntity(entityHitResult)
		if (level().isClientSide || level() !is ServerLevel) return
		if (impactServerTick >= 0) return

		applyHitToLiving(entityHitResult.entity as? LivingEntity ?: return, level() as ServerLevel)
		discard()
	}

	override fun onHitBlock(blockHitResult: BlockHitResult) {
		super.onHitBlock(blockHitResult)
		if (!level().isClientSide && level() is ServerLevel) {
			impactCancelled = true
			if (iceSlownessAppliesNow()) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(level().getEntity(beamTargetId) as? LivingEntity)
			}
			val pos = Vec3.atCenterOf(blockHitResult.blockPos)
			spawnSnowstormWorld(presentation.blockSplash, pos, 64.0)
			discard()
		}
	}

	private fun deliverScheduledImpact(serverLevel: ServerLevel) {
		if (impactDelivered) return
		impactDelivered = true
		val victim = serverLevel.getEntity(scheduledVictimEntityId) as? LivingEntity
		if (victim == null || !victim.isAlive) {
			if (pulseKind == LaprasPulseKind.ICE_BEAM) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(serverLevel.getEntity(beamTargetId) as? LivingEntity)
			}
			discard()
			return
		}
		applyHitToLiving(victim, serverLevel)
		discard()
	}

	private fun applyHitToLiving(victim: LivingEntity, serverLevel: ServerLevel) {
		val ownerEntity = owner

		playCobblemonSound(serverLevel, victim.position(), presentation.targetSoundPathPrimary)
		presentation.targetSoundPathSecondary?.let {
			playCobblemonSound(serverLevel, victim.position(), it)
		}

		val hitPos = victim.getEyePosition(1f)
		// Single world Snowstorm on hit (was target + splash = two stacked beams/bursts).
		spawnSnowstormWorld(presentation.targetEffect, hitPos)

		val src: DamageSource = when (ownerEntity) {
			is LivingEntity -> damageSources().thrown(this, ownerEntity)
			else -> damageSources().magic()
		}
		if (victim.hurt(src, pulseDamage.toFloat())) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, victim, src)
		}

		if (iceSlownessAppliesNow()) {
			LaprasIceBeamEffects.applyPostHitSlow(victim)
		}
	}

	override fun recreateFromPacket(packet: ClientboundAddEntityPacket) {
		super.recreateFromPacket(packet)
		val d = packet.xa
		val e = packet.ya
		val f = packet.za
		setDeltaMovement(d, e, f)
	}

	companion object {
		private val SOURCE_LOCATORS_BEAM: List<String> = listOf("special", "target")
		private val TARGET_LOCATORS_BEAM: List<String> = listOf("target")

		private const val WORLD_PARTICLE_RANGE = 128.0

		private const val IMPACT_PAD_TICKS: Int = 18

		private const val MIN_TRAVEL_TICKS: Int = 4
		private const val MAX_TRAVEL_TICKS: Int = 120

		fun computeImpactDelayTicks(
			origin: Vec3,
			targetEye: Vec3,
			blocksPerTick: Float,
			impactPadTicks: Int = IMPACT_PAD_TICKS,
		): Int {
			val dist = origin.distanceTo(targetEye)
			val travel = ceil(dist / blocksPerTick.toDouble()).toInt()
			return travel.coerceIn(MIN_TRAVEL_TICKS, MAX_TRAVEL_TICKS) + impactPadTicks
		}
	}

	private fun playCobblemonSound(level: ServerLevel, at: Vec3, path: String) {
		val id = ResourceLocation.fromNamespaceAndPath("cobblemon", path)
		val soundEvent: SoundEvent? = BuiltInRegistries.SOUND_EVENT.get(id)
		if (soundEvent != null) {
			level.playSound(null, at.x, at.y, at.z, soundEvent, SoundSource.NEUTRAL, 0.85f, 1.0f)
		}
	}
}
