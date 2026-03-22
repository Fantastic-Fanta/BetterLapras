package com.celestial_manta.betterlapras

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.minecraft.core.particles.ParticleTypes
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
 * Travelling pulse using Cobblemon Water Pulse Snowstorm effects + move sounds.
 * Damage and target hit VFX are applied on a **scheduled server tick** so they line up with travel + beam visuals
 * (the entity does not deal damage on collision).
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

	private var trailCounter: Int = 0

	/** Network id of the combat target; used to spawn world-space VFX on the target while the pulse flies. */
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

	/**
	 * @param impactTick absolute server tick when damage + target hit effects apply
	 * @param victimEntityId living target this pulse was aimed at
	 */
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
	}

	override fun getDefaultGravity(): Double = 0.02

	override fun canHitEntity(entity: Entity): Boolean {
		if (!super.canHitEntity(entity)) return false
		val owner = owner
		if (owner != null && entity == owner) return false
		// Scheduled hits: pass through entities; damage/VFX fire on [impactServerTick].
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
		// Until the scheduled impact tick, ignore block/entity hits — otherwise we discard in [onHitBlock]
		// before damage runs (entities are already ignored via [canHitEntity]).
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
			discard()
			return
		}

		setDeltaMovement(vec3.scale(0.99))
		applyGravity()
		setPos(x, y, z)

		if (!level().isClientSide && level() is ServerLevel) {
			trailCounter++
			val pos = position()
			// Cobblemon actor/suds/ring need entity-linked emitters (MoLang target_delta / q.entity_*); world-only = frozen.
			when (pulseKind) {
				LaprasPulseKind.HYDRO_PUMP -> {
					broadcastVanillaWaterTrailHydro(pos, vec3)
					broadcastEntityBeam()
				}
				LaprasPulseKind.OFF_TYPE_WATER -> {
					if (trailCounter % BEAM_INTERVAL_TICKS == 0) {
						broadcastEntityBeam()
					}
					if (trailCounter % SUDS_INTERVAL_TICKS == 0) {
						broadcastEntitySudsOnOwner()
					}
					if (trailCounter % RING_INTERVAL_TICKS == 0) {
						broadcastEntityRing()
					}
				}
				else -> {
					broadcastVanillaWaterTrail(pos, vec3)
					if (trailCounter % BEAM_INTERVAL_TICKS == 0) {
						broadcastEntityBeam()
					}
					if (trailCounter % SUDS_INTERVAL_TICKS == 0) {
						broadcastEntitySudsOnOwner()
					}
					if (trailCounter % RING_INTERVAL_TICKS == 0) {
						broadcastEntityRing()
					}
				}
			}
		}
	}

	private fun broadcastVanillaWaterTrail(pos: Vec3, motion: Vec3) {
		val sl = level() as ServerLevel
		val len = motion.length()
		if (len < 1.0e-4) return
		val spread = 0.12
		sl.sendParticles(
			ParticleTypes.SPLASH,
			pos.x,
			pos.y,
			pos.z,
			2,
			spread,
			spread * 0.5,
			spread,
			0.02,
		)
	}

	/** Chunkier trail so Hydro Pump reads as a thick straight jet (same SPLASH type as default water). */
	private fun broadcastVanillaWaterTrailHydro(pos: Vec3, motion: Vec3) {
		val sl = level() as ServerLevel
		val len = motion.length()
		if (len < 1.0e-4) return
		val spread = 0.38
		sl.sendParticles(
			ParticleTypes.SPLASH,
			pos.x,
			pos.y,
			pos.z,
			10,
			spread * 0.35,
			spread * 0.25,
			spread * 0.35,
			0.055,
		)
	}

	private fun spawnSnowstormWorld(effect: ResourceLocation, pos: Vec3, range: Double = WORLD_PARTICLE_RANGE) {
		// Cobblemon: last arg is exclusionCondition — true skips that player; never exclude here.
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

	private fun broadcastEntityBeam() {
		val lapras = owner as? PokemonEntity ?: return
		if (beamTargetId == 0) return
		val tgt = level().getEntity(beamTargetId) ?: return
		if (!tgt.isAlive || tgt !is LivingEntity) return
		broadcastEntitySnowstorm(
			SpawnSnowstormEntityParticlePacket(
				presentation.trailActor,
				lapras.id,
				SOURCE_LOCATORS_BEAM,
				tgt.id,
				TARGET_LOCATORS_BEAM,
			),
			lapras.x,
			lapras.y,
			lapras.z,
		)
	}

	private fun broadcastEntitySudsOnOwner() {
		val lapras = owner as? PokemonEntity ?: return
		broadcastEntitySnowstorm(
			SpawnSnowstormEntityParticlePacket(
				presentation.trailSuds,
				lapras.id,
				listOf("root"),
				null,
				emptyList(),
			),
			lapras.x,
			lapras.y,
			lapras.z,
		)
	}

	private fun broadcastEntityRing() {
		val lapras = owner as? PokemonEntity ?: return
		if (beamTargetId == 0) return
		val tgt = level().getEntity(beamTargetId) ?: return
		if (!tgt.isAlive || tgt !is LivingEntity) return
		broadcastEntitySnowstorm(
			SpawnSnowstormEntityParticlePacket(
				presentation.trailRing,
				lapras.id,
				SOURCE_LOCATORS_BEAM,
				tgt.id,
				TARGET_LOCATORS_BEAM,
			),
			lapras.x,
			lapras.y,
			lapras.z,
		)
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
			val pos = Vec3.atCenterOf(blockHitResult.blockPos)
			val blockRange = if (pulseKind == LaprasPulseKind.HYDRO_PUMP) HYDRO_HIT_PARTICLE_RANGE else 64.0
			spawnSnowstormWorld(presentation.blockSplash, pos, blockRange)
			discard()
		}
	}

	private fun deliverScheduledImpact(serverLevel: ServerLevel) {
		if (impactDelivered) return
		impactDelivered = true
		val victim = serverLevel.getEntity(scheduledVictimEntityId) as? LivingEntity
		if (victim == null || !victim.isAlive) {
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
		val hitRange = if (pulseKind == LaprasPulseKind.HYDRO_PUMP) HYDRO_HIT_PARTICLE_RANGE else WORLD_PARTICLE_RANGE
		spawnSnowstormWorld(presentation.targetEffect, hitPos, hitRange)
		spawnSnowstormWorld(presentation.targetSplash, hitPos, hitRange)
		if (pulseKind == LaprasPulseKind.HYDRO_PUMP) {
			spawnSnowstormWorld(presentation.targetEffect, hitPos.add(0.08, 0.0, 0.0), hitRange)
			spawnSnowstormWorld(presentation.targetSplash, hitPos.add(-0.06, 0.05, 0.0), hitRange)
		}

		val src: DamageSource = when (ownerEntity) {
			is LivingEntity -> damageSources().thrown(this, ownerEntity)
			else -> damageSources().magic()
		}
		if (victim.hurt(src, pulseDamage.toFloat())) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, victim, src)
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
		private const val HYDRO_HIT_PARTICLE_RANGE = 192.0

		private const val BEAM_INTERVAL_TICKS = 2
		private const val SUDS_INTERVAL_TICKS = 3
		private const val RING_INTERVAL_TICKS = 4

		/**
		 * Extra ticks after geometric flight time so target VFX/damage align with the Cobblemon pulse
		 * (~0.9s particle lifetime on `waterpulse_actor`).
		 */
		private const val IMPACT_PAD_TICKS: Int = 18

		private const val MIN_TRAVEL_TICKS: Int = 4
		private const val MAX_TRAVEL_TICKS: Int = 120

		/**
		 * Server ticks from fire until scheduled impact, from spawn point to target eye (rough straight-line
		 * travel at [blocksPerTick]) plus [IMPACT_PAD_TICKS].
		 */
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
