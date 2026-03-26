package com.celestial_manta.betterlapras;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * End-rod look with no drift: zero gravity and velocity so particles stay on the spawn point.
 * Short lifetime (vanilla end rod is 60 + nextInt(12)).
 */
@Environment(EnvType.CLIENT)
public final class PerishSongEndRodParticle extends SimpleAnimatedParticle {
	private static final int LIFETIME_BASE = 4;
	private static final int LIFETIME_SPREAD = 4;

	PerishSongEndRodParticle(
		ClientLevel level,
		double x,
		double y,
		double z,
		double xd,
		double yd,
		double zd,
		SpriteSet sprites
	) {
		super(level, x, y, z, sprites, 0.0F);
		this.quadSize *= 0.75F;
		this.lifetime = LIFETIME_BASE + this.random.nextInt(LIFETIME_SPREAD);
		this.setFadeColor(15916745);
		this.setSpriteFromAge(sprites);
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
	}

	@Override
	public void move(double dx, double dy, double dz) {
		this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
		this.setLocationFromBoundingbox();
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<SimpleParticleType> {
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
			double zd
		) {
			return new PerishSongEndRodParticle(level, x, y, z, xd, yd, zd, this.sprites);
		}
	}
}
