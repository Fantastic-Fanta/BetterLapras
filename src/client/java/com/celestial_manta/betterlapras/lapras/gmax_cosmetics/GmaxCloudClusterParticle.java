package com.celestial_manta.betterlapras.lapras.gmax_cosmetics;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Dense smoke clusters that orbit above a Gigantamax Lapras (Gmax-style storm
 * clouds), biased toward the entity’s back (opposite
 * {@link Entity#getLookAngle()}): two large masses plus smaller satellite
 * clusters on the same vertical layer with randomized sizes. Each puff uses
 * {@link net.minecraft.client.particle.SingleQuadParticle}'s {@code render}
 * path.
 */
@Environment(EnvType.CLIENT)
public final class GmaxCloudClusterParticle extends TextureSheetParticle {
	/** Short names for loops; values from {@link GmaxLaprasParticleConfig}. */
	private static final int PUFFS_PER_CLUSTER = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFFS_PER_CLUSTER;
	private static final float CLUSTER_SPREAD = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SPREAD;
	private static final float PUFF_QUAD_SIZE = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_QUAD_SIZE;
	private static final float ORBIT_RAD_PER_TICK = GmaxLaprasParticleConfig.CLOUD_CLUSTER_ORBIT_RAD_PER_TICK;
	private static final float VERTICAL_BOB_AMP = GmaxLaprasParticleConfig.CLOUD_CLUSTER_VERTICAL_BOB_AMP;
	private static final float BACK_OFFSET_BLOCKS = GmaxLaprasParticleConfig.CLOUD_CLUSTER_BACK_OFFSET_BLOCKS;
	private static final float RUMBLE_AMP = GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_AMP;
	private static final float RUMBLE_AMP_Y = GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_AMP_Y;
	private static final float QUAD_PULSE = GmaxLaprasParticleConfig.CLOUD_CLUSTER_QUAD_PULSE;
	private static final float TINT_R = GmaxLaprasParticleConfig.CLOUD_CLUSTER_TINT_R;
	private static final float TINT_G = GmaxLaprasParticleConfig.CLOUD_CLUSTER_TINT_G;
	private static final float TINT_B = GmaxLaprasParticleConfig.CLOUD_CLUSTER_TINT_B;
	private static final float TINT_A = GmaxLaprasParticleConfig.CLOUD_CLUSTER_TINT_A;

	private static final int SATELLITE_COUNT = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_COUNT;
	private static final int PUFFS_PER_SATELLITE = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_PUFFS;
	private static final float SATELLITE_SPREAD = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SPREAD
			* GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_SPREAD_FACTOR;

	private final int targetEntityId;
	private final float orbitRadius;
	private final float cloudYOffset;
	private final SpriteSet spriteSet;
	private final TextureAtlasSprite[] puffSprites;
	private final Vector3f[][] puffOffsets;
	private final float[] puffScales;
	private final float[] puffAlphas;
	/** Independent phase/speed per puff for internal rumble. */
	private final Vector3f[] rumblePhase;
	private final float[] rumbleSpeed;
	private final TextureAtlasSprite[] satellitePuffSprites;
	private final Vector3f[][] satellitePuffOffsets;
	private final float[] satellitePuffScales;
	private final float[] satellitePuffAlphas;
	private final Vector3f[] satelliteRumblePhase;
	private final float[] satelliteRumbleSpeed;
	/** Initial angle (rad) on the orbit ring for each satellite cluster. */
	private final float[] satelliteAngleRad;
	/** Orbit radius multiplier vs [orbitRadius] (smaller than main pair). */
	private final float[] satelliteOrbitFactor;
	private float orbitAngleRad;

	GmaxCloudClusterParticle(
			ClientLevel level,
			double x,
			double y,
			double z,
			double orbitRadius,
			double entityIdParam,
			double cloudYOffset,
			SpriteSet sprites) {
		super(level, x, y, z, 0.0, 0.0, 0.0);
		this.spriteSet = sprites;
		this.targetEntityId = (int) Math.round(entityIdParam);
		this.orbitRadius = (float) orbitRadius;
		this.cloudYOffset = (float) cloudYOffset;
		this.hasPhysics = false;
		this.lifetime = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PARTICLE_LIFETIME_TICKS;
		this.rCol = TINT_R;
		this.gCol = TINT_G;
		this.bCol = TINT_B;
		this.alpha = GmaxLaprasParticleConfig.CLOUD_CLUSTER_MAIN_ALPHA;

		this.puffSprites = new TextureAtlasSprite[PUFFS_PER_CLUSTER * 2];
		this.puffOffsets = new Vector3f[2][PUFFS_PER_CLUSTER];
		this.puffScales = new float[PUFFS_PER_CLUSTER * 2];
		this.puffAlphas = new float[PUFFS_PER_CLUSTER * 2];
		this.rumblePhase = new Vector3f[PUFFS_PER_CLUSTER * 2];
		this.rumbleSpeed = new float[PUFFS_PER_CLUSTER * 2];
		for (int c = 0; c < 2; c++) {
			for (int i = 0; i < PUFFS_PER_CLUSTER; i++) {
				int idx = c * PUFFS_PER_CLUSTER + i;
				this.puffSprites[idx] = this.spriteSet.get(this.random);
				float ox = (this.random.nextFloat() - 0.5F) * 2.0F * CLUSTER_SPREAD;
				float oy = (this.random.nextFloat() - 0.5F) * 1.35F * CLUSTER_SPREAD;
				float oz = (this.random.nextFloat() - 0.5F) * 2.0F * CLUSTER_SPREAD;
				this.puffOffsets[c][i] = new Vector3f(ox, oy, oz);
				this.puffScales[idx] = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_SCALE_MIN
						+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_SCALE_SPREAD;
				this.puffAlphas[idx] = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_ALPHA_MIN
						+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_ALPHA_SPREAD;
				this.rumblePhase[idx] = new Vector3f(
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI);
				this.rumbleSpeed[idx] = GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_SPEED_MIN
						+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_SPEED_SPREAD;
			}
		}
		int satPuffTotal = SATELLITE_COUNT * PUFFS_PER_SATELLITE;
		this.satellitePuffSprites = new TextureAtlasSprite[satPuffTotal];
		this.satellitePuffOffsets = new Vector3f[SATELLITE_COUNT][PUFFS_PER_SATELLITE];
		this.satellitePuffScales = new float[satPuffTotal];
		this.satellitePuffAlphas = new float[satPuffTotal];
		this.satelliteRumblePhase = new Vector3f[satPuffTotal];
		this.satelliteRumbleSpeed = new float[satPuffTotal];
		this.satelliteAngleRad = new float[SATELLITE_COUNT];
		this.satelliteOrbitFactor = new float[SATELLITE_COUNT];
		float satScaleMin = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_SCALE_MIN;
		float satScaleMax = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_SCALE_MAX;
		float orbitMin = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_ORBIT_MIN;
		float orbitMax = GmaxLaprasParticleConfig.CLOUD_CLUSTER_SATELLITE_ORBIT_MAX;
		for (int s = 0; s < SATELLITE_COUNT; s++) {
			this.satelliteAngleRad[s] = this.random.nextFloat() * Mth.TWO_PI;
			this.satelliteOrbitFactor[s] = orbitMin + this.random.nextFloat() * (orbitMax - orbitMin);
			float clusterMul = satScaleMin + this.random.nextFloat() * (satScaleMax - satScaleMin);
			for (int i = 0; i < PUFFS_PER_SATELLITE; i++) {
				int idx = s * PUFFS_PER_SATELLITE + i;
				this.satellitePuffSprites[idx] = this.spriteSet.get(this.random);
				float ox = (this.random.nextFloat() - 0.5F) * 2.0F * SATELLITE_SPREAD;
				float oy = (this.random.nextFloat() - 0.5F) * 1.35F * SATELLITE_SPREAD;
				float oz = (this.random.nextFloat() - 0.5F) * 2.0F * SATELLITE_SPREAD;
				this.satellitePuffOffsets[s][i] = new Vector3f(ox, oy, oz);
				this.satellitePuffScales[idx] = clusterMul
						* (GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_SCALE_MIN
								+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_SCALE_SPREAD);
				this.satellitePuffAlphas[idx] = GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_ALPHA_MIN
						+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_PUFF_ALPHA_SPREAD;
				this.satelliteRumblePhase[idx] = new Vector3f(
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI);
				this.satelliteRumbleSpeed[idx] = GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_SPEED_MIN
						+ this.random.nextFloat() * GmaxLaprasParticleConfig.CLOUD_CLUSTER_RUMBLE_SPEED_SPREAD;
			}
		}
		this.orbitAngleRad = this.random.nextFloat() * Mth.TWO_PI;
		// Base size for puffs (SingleQuadParticle ctor sets a small random quadSize).
		this.quadSize = PUFF_QUAD_SIZE;
		this.pickSprite(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public void tick() {
		super.tick();
		this.orbitAngleRad += ORBIT_RAD_PER_TICK;
		if (this.targetEntityId != 0) {
			Entity e = this.level.getEntity(this.targetEntityId);
			if (e == null || !e.isAlive()) {
				this.remove();
				return;
			}
			if (!GmaxLaprasForceFieldClient.shouldKeepForceFieldRingFor(e)) {
				this.remove();
				return;
			}
			this.x = e.getX();
			this.y = e.getY();
			this.z = e.getZ();
		}
	}

	/** Tiny phase split so the two clusters don't bob identically. */
	private static float offYForCluster(int c) {
		return c == 0 ? 0.04F : -0.04F;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		double saveX = this.x;
		double saveY = this.y;
		double saveZ = this.z;
		double saveXo = this.xo;
		double saveYo = this.yo;
		double saveZo = this.zo;
		float saveQuad = this.quadSize;
		float saveR = this.rCol;
		float saveG = this.gCol;
		float saveB = this.bCol;
		float saveA = this.alpha;

		float px = (float) Mth.lerp(partialTicks, this.xo, this.x);
		float py = (float) Mth.lerp(partialTicks, this.yo, this.y);
		float pz = (float) Mth.lerp(partialTicks, this.zo, this.z);
		float bob = Mth.sin((this.age + partialTicks) * 0.06F) * VERTICAL_BOB_AMP;
		float orbitAngle = this.orbitAngleRad + ORBIT_RAD_PER_TICK * partialTicks;

		double backX = 0.0;
		double backZ = 0.0;
		if (this.targetEntityId != 0) {
			Entity e = this.level.getEntity(this.targetEntityId);
			if (e != null) {
				Vec3 look = e.getLookAngle();
				double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
				if (hLen > 1.0E-4) {
					backX = -look.x / hLen * BACK_OFFSET_BLOCKS;
					backZ = -look.z / hLen * BACK_OFFSET_BLOCKS;
				}
			}
		}

		double baseCloudY = py + this.cloudYOffset + bob;

		for (int c = 0; c < 2; c++) {
			float theta = orbitAngle + c * Mth.PI;
			float ocx = Mth.cos(theta) * this.orbitRadius;
			float ocz = Mth.sin(theta) * this.orbitRadius;
			double cx = px + ocx + backX;
			double cy = baseCloudY + offYForCluster(c);
			double cz = pz + ocz + backZ;
			this.renderClusterPuffs(
					buffer,
					camera,
					partialTicks,
					false,
					this.puffOffsets[c],
					c * PUFFS_PER_CLUSTER,
					PUFFS_PER_CLUSTER,
					this.puffSprites,
					cx,
					cy,
					cz);
		}

		for (int s = 0; s < SATELLITE_COUNT; s++) {
			float theta = orbitAngle + this.satelliteAngleRad[s];
			float rad = this.orbitRadius * this.satelliteOrbitFactor[s];
			float ocx = Mth.cos(theta) * rad;
			float ocz = Mth.sin(theta) * rad;
			double sx = px + ocx + backX;
			double sy = baseCloudY;
			double sz = pz + ocz + backZ;
			this.renderClusterPuffs(
					buffer,
					camera,
					partialTicks,
					true,
					this.satellitePuffOffsets[s],
					s * PUFFS_PER_SATELLITE,
					PUFFS_PER_SATELLITE,
					this.satellitePuffSprites,
					sx,
					sy,
					sz);
		}

		this.x = saveX;
		this.y = saveY;
		this.z = saveZ;
		this.xo = saveXo;
		this.yo = saveYo;
		this.zo = saveZo;
		this.quadSize = saveQuad;
		this.rCol = saveR;
		this.gCol = saveG;
		this.bCol = saveB;
		this.alpha = saveA;
		this.setSprite(this.puffSprites[0]);
	}

	private void renderClusterPuffs(
			VertexConsumer buffer,
			Camera camera,
			float partialTicks,
			boolean satelliteSlice,
			Vector3f[] offsets,
			int idxBase,
			int puffCount,
			TextureAtlasSprite[] sprites,
			double centerX,
			double centerY,
			double centerZ) {
		float wt = this.age + partialTicks;
		for (int i = 0; i < puffCount; i++) {
			int idx = idxBase + i;
			Vector3f off = offsets[i];
			Vector3f rp = satelliteSlice ? this.satelliteRumblePhase[idx] : this.rumblePhase[idx];
			float sp = satelliteSlice ? this.satelliteRumbleSpeed[idx] : this.rumbleSpeed[idx];
			float[] scales = satelliteSlice ? this.satellitePuffScales : this.puffScales;
			float[] alphas = satelliteSlice ? this.satellitePuffAlphas : this.puffAlphas;
			float rx = Mth.sin(wt * sp + rp.x) * RUMBLE_AMP;
			float ry = Mth.cos(wt * sp * 0.71F + rp.y) * RUMBLE_AMP_Y;
			float rz = Mth.sin(wt * sp * 1.13F + rp.z) * RUMBLE_AMP;
			double puffX = centerX + off.x + rx;
			double puffY = centerY + off.y + ry;
			double puffZ = centerZ + off.z + rz;
			this.x = this.xo = puffX;
			this.y = this.yo = puffY;
			this.z = this.zo = puffZ;
			this.setSprite(sprites[idx]);
			float pulse = 1.0F + QUAD_PULSE * Mth.sin(wt * 0.17F + rp.x * 0.4F);
			this.quadSize = PUFF_QUAD_SIZE * scales[idx] * pulse;
			this.rCol = TINT_R;
			this.gCol = TINT_G;
			this.bCol = TINT_B;
			this.alpha = Mth.clamp(TINT_A * alphas[idx], 0.0F, 1.0F);
			super.render(buffer, camera, partialTicks);
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(
				SimpleParticleType type,
				ClientLevel level,
				double x,
				double y,
				double z,
				double xd,
				double yd,
				double zd) {
			return new GmaxCloudClusterParticle(level, x, y, z, xd, yd, zd, this.sprites);
		}
	}
}
