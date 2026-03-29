package com.celestial_manta.betterlapras.lapras.gmax_cosmetics;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.celestial_manta.betterlapras.BetterLaprasParticles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Two dense smoke clusters that orbit above a Gigantamax Lapras (Gmax-style
 * storm clouds), biased toward the entity’s back (opposite
 * {@link Entity#getLookAngle()}). Short-lived
 * {@link GmaxCloudTrailPuffParticle}s are spawned each tick along the path so
 * clouds linger ~1s and fade. Each main puff uses
 * {@link net.minecraft.client.particle.SingleQuadParticle}'s {@code render}
 * path.
 */
@Environment(EnvType.CLIENT)
public final class GmaxCloudClusterParticle extends TextureSheetParticle {
	private static final int PUFFS_PER_CLUSTER = 52;
	/**
	 * Horizontal spread of each cluster (blocks); lower = tighter / denser cloud.
	 */
	private static final float CLUSTER_SPREAD = 0.82F;
	/** Billboard half-size scale for each puff. */
	private static final float PUFF_QUAD_SIZE = 0.55F;
	/** Orbit speed around the entity (rad/tick). */
	private static final float ORBIT_RAD_PER_TICK = 0.04F;
	/** Gentle bob (blocks). */
	private static final float VERTICAL_BOB_AMP = 0.5F;
	/**
	 * Shift cluster centers toward the entity's back (opposite look), horizontal
	 * (blocks).
	 */
	private static final float BACK_OFFSET_BLOCKS = 2.0F;
	/** Client-only puffs dropped along the orbit each tick (~1s lifetime each). */
	private static final int TRAIL_PUFFS_PER_CLUSTER = 7;
	private static final double TRAIL_SPAWN_JITTER = 0.55;
	/** Per-puff slow oscillation inside the cluster (blocks). */
	private static final float RUMBLE_AMP = 0.2F;
	private static final float RUMBLE_AMP_Y = 0.14F;
	/** Slight breathing on quad size so the mass feels alive. */
	private static final float QUAD_PULSE = 0.07F;
	/** Dark red tint (multiplies sprite). */
	private static final float TINT_R = 0.52F;
	private static final float TINT_G = 0.07F;
	private static final float TINT_B = 0.09F;
	private static final float TINT_A = 0.94F;

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
		this.lifetime = 6000;
		this.rCol = TINT_R;
		this.gCol = TINT_G;
		this.bCol = TINT_B;
		this.alpha = 0.85f;

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
				this.puffScales[idx] = 0.72F + this.random.nextFloat() * 0.45F;
				this.puffAlphas[idx] = 0.68F + this.random.nextFloat() * 0.28F;
				this.rumblePhase[idx] = new Vector3f(
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI,
						this.random.nextFloat() * Mth.TWO_PI);
				this.rumbleSpeed[idx] = 0.052F + this.random.nextFloat() * 0.095F;
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
			float bob = Mth.sin(this.age * 0.06F) * VERTICAL_BOB_AMP;
			double bx = 0.0;
			double bz = 0.0;
			Vec3 look = e.getLookAngle();
			double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
			if (hLen > 1.0E-4) {
				bx = -look.x / hLen * BACK_OFFSET_BLOCKS;
				bz = -look.z / hLen * BACK_OFFSET_BLOCKS;
			}
			for (int c = 0; c < 2; c++) {
				float theta = this.orbitAngleRad + c * Mth.PI;
				float ocx = Mth.cos(theta) * this.orbitRadius;
				float ocz = Mth.sin(theta) * this.orbitRadius;
				double cx = e.getX() + ocx + bx;
				double cy = e.getY() + this.cloudYOffset + bob + offYForCluster(c);
				double cz = e.getZ() + ocz + bz;
				for (int k = 0; k < TRAIL_PUFFS_PER_CLUSTER; k++) {
					double jx = (this.random.nextDouble() - 0.5) * 2.0 * TRAIL_SPAWN_JITTER;
					double jy = (this.random.nextDouble() - 0.5) * 2.0 * TRAIL_SPAWN_JITTER * 0.55;
					double jz = (this.random.nextDouble() - 0.5) * 2.0 * TRAIL_SPAWN_JITTER;
					double vx = (this.random.nextDouble() - 0.5) * 0.052;
					double vy = (this.random.nextDouble() - 0.5) * 0.034;
					double vz = (this.random.nextDouble() - 0.5) * 0.052;
					this.level.addParticle(
							BetterLaprasParticles.GMAX_CLOUD_TRAIL_PUFF,
							true,
							cx + jx,
							cy + jy,
							cz + jz,
							vx,
							vy,
							vz);
				}
			}
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

		for (int c = 0; c < 2; c++) {
			float theta = orbitAngle + c * Mth.PI;
			float ocx = Mth.cos(theta) * this.orbitRadius;
			float ocz = Mth.sin(theta) * this.orbitRadius;
			double cx = px + ocx + backX;
			double cy = py + this.cloudYOffset + bob + offYForCluster(c);
			double cz = pz + ocz + backZ;
			this.renderClusterPuffs(buffer, camera, partialTicks, c, cx, cy, cz);
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
			int clusterIndex,
			double centerX,
			double centerY,
			double centerZ) {
		float wt = this.age + partialTicks;
		for (int i = 0; i < PUFFS_PER_CLUSTER; i++) {
			int idx = clusterIndex * PUFFS_PER_CLUSTER + i;
			Vector3f off = this.puffOffsets[clusterIndex][i];
			Vector3f rp = this.rumblePhase[idx];
			float sp = this.rumbleSpeed[idx];
			float rx = Mth.sin(wt * sp + rp.x) * RUMBLE_AMP;
			float ry = Mth.cos(wt * sp * 0.71F + rp.y) * RUMBLE_AMP_Y;
			float rz = Mth.sin(wt * sp * 1.13F + rp.z) * RUMBLE_AMP;
			double puffX = centerX + off.x + rx;
			double puffY = centerY + off.y + ry;
			double puffZ = centerZ + off.z + rz;
			this.x = this.xo = puffX;
			this.y = this.yo = puffY;
			this.z = this.zo = puffZ;
			this.setSprite(this.puffSprites[idx]);
			float pulse = 1.0F + QUAD_PULSE * Mth.sin(wt * 0.17F + rp.x * 0.4F);
			this.quadSize = PUFF_QUAD_SIZE * this.puffScales[idx] * pulse;
			this.rCol = TINT_R;
			this.gCol = TINT_G;
			this.bCol = TINT_B;
			this.alpha = Mth.clamp(TINT_A * this.puffAlphas[idx], 0.0F, 1.0F);
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
