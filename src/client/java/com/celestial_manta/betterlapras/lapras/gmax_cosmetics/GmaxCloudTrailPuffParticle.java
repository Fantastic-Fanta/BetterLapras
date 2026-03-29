package com.celestial_manta.betterlapras.lapras.gmax_cosmetics;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Short-lived puff (~1s) along the Gmax cloud orbit; uses {@code assets/.../textures/particle/cloud.png}
 * and shrinks over its lifetime (trail size falloff).
 */
@Environment(EnvType.CLIENT)
public final class GmaxCloudTrailPuffParticle extends TextureSheetParticle {
	/** ~1 second at 20 ticks/s. */
	private static final int LIFETIME_TICKS = 20;
	private static final float TINT_R = 0.52F;
	private static final float TINT_G = 0.08F;
	private static final float TINT_B = 0.1F;
	/** Shrink to this fraction of {@link #baseQuadSize} by end of life (trail falloff). */
	private static final float SIZE_END_FRAC = 0.18F;

	/** Initial billboard scale after random variation (used for lifetime size falloff). */
	private final float baseQuadSize;
	private final float breathePhase;

	private GmaxCloudTrailPuffParticle(
			ClientLevel level,
			double x,
			double y,
			double z,
			double xd,
			double yd,
			double zd,
			SpriteSet sprites) {
		super(level, x, y, z, xd, yd, zd);
		this.lifetime = LIFETIME_TICKS;
		this.friction = 1.0F;
		this.hasPhysics = false;
		this.breathePhase = this.random.nextFloat() * Mth.TWO_PI;
		this.rCol = TINT_R;
		this.gCol = TINT_G;
		this.bCol = TINT_B;
		this.alpha = 0.92F;
		this.quadSize *= 0.85F + this.random.nextFloat() * 0.35F;
		this.baseQuadSize = this.quadSize;
		this.pickSprite(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick();
		float t = (float) this.age / (float) this.lifetime;
		this.alpha = Mth.clamp(0.92F * (1.0F - t * t), 0.0F, 1.0F);
		// Trail: shrink over life (smooth ease; matches alpha feel).
		float sizeT = t * t;
		float sizeMul = Mth.lerp(1.0F, SIZE_END_FRAC, sizeT);
		float breathe = 1.0F + 0.065F * Mth.sin(this.age * 0.48F + this.breathePhase);
		this.quadSize = this.baseQuadSize * sizeMul * breathe;
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
			return new GmaxCloudTrailPuffParticle(level, x, y, z, xd, yd, zd, this.sprites);
		}
	}
}
