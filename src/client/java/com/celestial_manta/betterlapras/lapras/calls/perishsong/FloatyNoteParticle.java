package com.celestial_manta.betterlapras.lapras.calls.perishsong;

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
 * Same look as {@link net.minecraft.client.particle.NoteParticle} (opaque note sheet, pitch-based
 * color) with no motion — stays where spawned until it expires.
 */
@Environment(EnvType.CLIENT)
public final class FloatyNoteParticle extends TextureSheetParticle {
	private FloatyNoteParticle(ClientLevel level, double x, double y, double z, double pitch) {
		super(level, x, y, z);
		this.friction = 1.0F;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.applyPitchColor(pitch);
		this.quadSize *= 1.5F;
		this.lifetime = 24 + this.random.nextInt(12);
	}

	private void applyPitchColor(double pitch) {
		float p = (float) pitch;
		this.rCol = Math.max(0.0F, Mth.sin((p + 0.0F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
		this.gCol = Math.max(0.0F, Mth.sin((p + 0.33333334F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
		this.bCol = Math.max(0.0F, Mth.sin((p + 0.6666667F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	public float getQuadSize(float partialTick) {
		return this.quadSize * Mth.clamp((this.age + partialTick) / (float) this.lifetime * 32.0F, 0.0F, 1.0F);
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
			// Match vanilla note: first velocity component is treated as pitch for color.
			FloatyNoteParticle p = new FloatyNoteParticle(level, x, y, z, xd);
			p.pickSprite(this.sprites);
			return p;
		}
	}
}
