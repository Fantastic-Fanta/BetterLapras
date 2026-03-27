package com.celestial_manta.betterlapras.lapras.moves.projectile

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.celestial_manta.betterlapras.lapras.ai.betterLaprasStrikeTargetPoint
import com.celestial_manta.betterlapras.lapras.moves.effects.LaprasIceBeamEffects
import com.celestial_manta.betterlapras.lapras.moves.effects.LaprasSheerColdCone
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
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
import kotlin.math.ceil

/**
 * Server-driven “move strike” entity: Cobblemon Snowstorm trail(s) from Lapras to target, scheduled impact damage,
 * and profile-specific hit logic. Visuals come from [PulsePresentation] (any move’s Cobblemon effect ids).
 */
class LaprasMoveProjectile(
	type: EntityType<out LaprasMoveProjectile>,
	level: Level,
) : Projectile(type, level) {

	var strikeDamage: Double = 1.0
		private set

	private var moveProfileKind: LaprasPulseKind = LaprasPulseKind.WATER_GUN_OFFTYPE
	private var presentation: PulsePresentation =
		LaprasMoveShotProfiles.presentationForKind(LaprasPulseKind.WATER_GUN_OFFTYPE)

	private var trailBurstSent: Boolean = false

	private var beamTargetId: Int = 0

	/** Sheer Cold cone: mouth origin and aimed strike point (world space) at spawn. */
	private var sheerColdApex: Vec3? = null
	private var sheerColdTargetPoint: Vec3? = null

	private var impactServerTick: Int = -1
	private var scheduledVictimEntityId: Int = 0
	private var impactDelivered: Boolean = false
	private var impactCancelled: Boolean = false

	fun setStrikeDamage(value: Double) {
		strikeDamage = value
	}

	/** Binds [LaprasPulseKind] + resolved [PulsePresentation] (NBT: [LaprasPulseKind.NBT_KIND_ID]). */
	fun applyMoveProfile(kind: LaprasPulseKind) {
		moveProfileKind = kind
		presentation = LaprasMoveShotProfiles.presentationForKind(kind)
	}

	fun setBeamTarget(target: Entity) {
		beamTargetId = target.id
	}

	fun setScheduledImpact(impactTick: Int, victimEntityId: Int) {
		impactServerTick = impactTick
		scheduledVictimEntityId = victimEntityId
	}

	fun setSheerColdCone(apex: Vec3, targetPoint: Vec3) {
		sheerColdApex = apex
		sheerColdTargetPoint = targetPoint
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

	override fun addAdditionalSaveData(tag: CompoundTag) {
		super.addAdditionalSaveData(tag)
		tag.putDouble(NBT_STRIKE_DAMAGE, strikeDamage)
		tag.putString(LaprasPulseKind.NBT_KIND_ID, moveProfileKind.name)
		tag.putInt("BetterLaprasImpactTick", impactServerTick)
		tag.putInt("BetterLaprasVictimId", scheduledVictimEntityId)
		tag.putBoolean("BetterLaprasImpactDelivered", impactDelivered)
		tag.putBoolean("BetterLaprasImpactCancelled", impactCancelled)
		tag.putInt("BetterLaprasBeamTarget", beamTargetId)
		tag.putBoolean("BetterLaprasTrailPulseEmitted", trailBurstSent)
		if (sheerColdApex != null && sheerColdTargetPoint != null) {
			tag.putBoolean("BetterLaprasSheerCone", true)
			tag.putDouble("BetterLaprasSheerAx", sheerColdApex!!.x)
			tag.putDouble("BetterLaprasSheerAy", sheerColdApex!!.y)
			tag.putDouble("BetterLaprasSheerAz", sheerColdApex!!.z)
			tag.putDouble("BetterLaprasSheerTx", sheerColdTargetPoint!!.x)
			tag.putDouble("BetterLaprasSheerTy", sheerColdTargetPoint!!.y)
			tag.putDouble("BetterLaprasSheerTz", sheerColdTargetPoint!!.z)
		}
	}

	override fun readAdditionalSaveData(tag: CompoundTag) {
		super.readAdditionalSaveData(tag)
		strikeDamage = when {
			tag.contains(NBT_STRIKE_DAMAGE) -> tag.getDouble(NBT_STRIKE_DAMAGE)
			tag.contains(NBT_STRIKE_DAMAGE_LEGACY) -> tag.getDouble(NBT_STRIKE_DAMAGE_LEGACY)
			else -> strikeDamage
		}
		when {
			tag.contains(LaprasPulseKind.NBT_KIND_ID) ->
				applyMoveProfile(LaprasPulseKind.fromPersistentId(tag.getString(LaprasPulseKind.NBT_KIND_ID)))
			tag.contains(LaprasPulseKind.NBT_KIND_LEGACY_ORDINAL) ->
				applyMoveProfile(LaprasPulseKind.fromLegacyOrdinal(tag.getInt(LaprasPulseKind.NBT_KIND_LEGACY_ORDINAL)))
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
			trailBurstSent = tag.getBoolean("BetterLaprasTrailPulseEmitted")
		}
		if (tag.getBoolean("BetterLaprasSheerCone")) {
			sheerColdApex = Vec3(
				tag.getDouble("BetterLaprasSheerAx"),
				tag.getDouble("BetterLaprasSheerAy"),
				tag.getDouble("BetterLaprasSheerAz"),
			)
			sheerColdTargetPoint = Vec3(
				tag.getDouble("BetterLaprasSheerTx"),
				tag.getDouble("BetterLaprasSheerTy"),
				tag.getDouble("BetterLaprasSheerTz"),
			)
		} else {
			sheerColdApex = null
			sheerColdTargetPoint = null
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
			if (shouldClearPreBeamFreezeOnMiss()) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(
					level().getEntity(beamTargetId) as? LivingEntity,
					owner as? LivingEntity,
				)
			}
			discard()
			return
		}

		setDeltaMovement(vec3.scale(0.99))
		applyGravity()
		setPos(x, y, z)

		if (!level().isClientSide && level() is ServerLevel && !trailBurstSent) {
			when {
				tryEmitTrailBurstsOnce() -> trailBurstSent = true
				beamTargetLostOrDead() -> trailBurstSent = true
			}
		}
	}

	private fun shouldClearPreBeamFreezeOnMiss(): Boolean =
		moveProfileKind == LaprasPulseKind.ICE_BEAM_MOVE

	private fun shouldApplyPostHitIceSlow(): Boolean =
		moveProfileKind == LaprasPulseKind.ICE_BEAM_MOVE || moveProfileKind == LaprasPulseKind.ICE_SHARD

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

	private fun tryEmitTrailBurstsOnce(): Boolean {
		val lapras = owner as? PokemonEntity ?: return false
		if (beamTargetId == 0) return false
		val tgt = level().getEntity(beamTargetId) ?: return false
		if (!tgt.isAlive || tgt !is LivingEntity) return false
		val srcLoc = presentation.trailSourceLocators
		val tgtLoc = presentation.trailTargetLocators
		fun send(effect: ResourceLocation) {
			broadcastEntitySnowstorm(
				SpawnSnowstormEntityParticlePacket(
					effect,
					lapras.id,
					srcLoc,
					tgt.id,
					tgtLoc,
				),
				lapras.x,
				lapras.y,
				lapras.z,
			)
		}
		send(presentation.trailPulseEffect)
		presentation.trailSecondaryPulseEffect?.let { send(it) }
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
			if (shouldClearPreBeamFreezeOnMiss()) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(
					level().getEntity(beamTargetId) as? LivingEntity,
					owner as? LivingEntity,
				)
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
			if (shouldClearPreBeamFreezeOnMiss()) {
				LaprasIceBeamEffects.clearHeavySlownessFromMiss(
					serverLevel.getEntity(beamTargetId) as? LivingEntity,
					owner as? LivingEntity,
				)
			}
			discard()
			return
		}
		if (moveProfileKind == LaprasPulseKind.SHEER_COLD) {
			val apex = sheerColdApex
			val aim = sheerColdTargetPoint
			if (apex != null && aim != null) {
				applySheerColdConeImpact(serverLevel, victim, apex, aim)
			} else {
				applyHitToLiving(victim, serverLevel)
			}
		} else {
			applyHitToLiving(victim, serverLevel)
		}
		discard()
	}

	private fun applySheerColdConeImpact(
		serverLevel: ServerLevel,
		primaryVictim: LivingEntity,
		apex: Vec3,
		targetPoint: Vec3,
	) {
		val lapras = owner as? PokemonEntity ?: run {
			applyHitToLiving(primaryVictim, serverLevel)
			return
		}
		val ownerPlayer = lapras.ownerUUID?.let { serverLevel.server.playerList.getPlayer(it) }

		dealSheerColdToVictim(serverLevel, primaryVictim, strikeDamage, playFullSounds = true)

		val box = LaprasSheerColdCone.queryRoughAabb(apex, targetPoint)
		for (entity in serverLevel.getEntitiesOfClass(LivingEntity::class.java, box)) {
			if (!entity.isAlive) continue
			if (entity == lapras) continue
			if (ownerPlayer != null && entity == ownerPlayer) continue
			if (entity.id == primaryVictim.id) continue
			val sample = LaprasSheerColdCone.entitySamplePoint(entity)
			if (!LaprasSheerColdCone.isInCone(apex, targetPoint, sample)) continue
			dealSheerColdToVictim(serverLevel, entity, strikeDamage * 0.5, playFullSounds = false)
		}
	}

	private fun dealSheerColdToVictim(
		serverLevel: ServerLevel,
		victim: LivingEntity,
		damage: Double,
		playFullSounds: Boolean,
	) {
		val ownerEntity = owner
		if (playFullSounds) {
			playCobblemonSound(serverLevel, victim.position(), presentation.targetSoundPathPrimary)
			presentation.targetSoundPathSecondary?.let {
				playCobblemonSound(serverLevel, victim.position(), it)
			}
		}
		val hitPos = victim.betterLaprasStrikeTargetPoint(1f)
		spawnSnowstormWorld(presentation.targetEffect, hitPos)
		val src: DamageSource = when (ownerEntity) {
			is LivingEntity -> damageSources().thrown(this, ownerEntity)
			else -> damageSources().magic()
		}
		if (victim.hurt(src, damage.toFloat())) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, victim, src)
		}
		LaprasIceBeamEffects.applyPostHitSlow(victim)
	}

	private fun applyHitToLiving(victim: LivingEntity, serverLevel: ServerLevel) {
		val ownerEntity = owner

		playCobblemonSound(serverLevel, victim.position(), presentation.targetSoundPathPrimary)
		presentation.targetSoundPathSecondary?.let {
			playCobblemonSound(serverLevel, victim.position(), it)
		}

		val hitPos = victim.betterLaprasStrikeTargetPoint(1f)
		spawnSnowstormWorld(presentation.targetEffect, hitPos)

		val src: DamageSource = when (ownerEntity) {
			is LivingEntity -> damageSources().thrown(this, ownerEntity)
			else -> damageSources().magic()
		}
		if (victim.hurt(src, strikeDamage.toFloat())) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, victim, src)
		}

		if (moveProfileKind == LaprasPulseKind.ICE_BEAM_MOVE) {
			LaprasIceBeamEffects.clearPreBeamSelfSlow(ownerEntity as? LivingEntity)
		}
		if (shouldApplyPostHitIceSlow()) {
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
		private const val NBT_STRIKE_DAMAGE = "BetterLaprasStrikeDamage"
		private const val NBT_STRIKE_DAMAGE_LEGACY = "PulseDamage"

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
