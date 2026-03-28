package com.celestial_manta.betterlapras.lapras.gmax_cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Epic Fight–inspired horizontal ring: quads around Lapras with UV-tiled
 * texture, rotating each tick.
 * <p>
 * <b>Custom image:</b> put a PNG at
 * {@code assets/betterlapras/textures/particle/gmax_force_field_ring.png} (see
 * {@link #RING_TEXTURE}).
 * Use {@link #TILES_AROUND_RING} for how many times the image repeats around
 * the ring, and
 * {@link #SUBDIVS_PER_TILE} for tessellation (more = smoother curve, more quads
 * per tile).
 * <p>
 * <b>Vertical sine:</b> the ribbon follows {@link #VERTICAL_WAVE_AMPLITUDE} ·
 * sin({@link #VERTICAL_WAVE_CYCLES_AROUND_RING}·θ + phase) in local Y while the
 * radius in XZ stays fixed (still a circle around Lapras). Phase can advance
 * for a
 * traveling wave ({@link #VERTICAL_WAVE_PHASE_RAD_PER_TICK}).
 */
@Environment(EnvType.CLIENT)
public final class GmaxForceFieldRingParticle extends Particle {
	/**
	 * Custom tile:
	 * {@code assets/betterlapras/textures/particle/gmax_force_field_ring.png}.
	 */
	private static final ResourceLocation RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			"betterlapras",
			"textures/particle/gmax_force_field_ring.png");
	/**
	 * How many times the texture wraps horizontally around the full 360° ring (like
	 * repeating the image
	 * around the circumference). 1 = one copy of the texture end-to-end.
	 */
	private static final int TILES_AROUND_RING = 1;
	/**
	 * Quad count per tile along the arc (tessellation). Total segments =
	 * {@code TILES_AROUND_RING * this}.
	 */
	private static final int SUBDIVS_PER_TILE = 32;
	private static final int SEGMENTS = TILES_AROUND_RING * SUBDIVS_PER_TILE;
	/** Taller ribbon = thicker band (world units). */
	private static final float QUAD_HEIGHT = 2.0F;
	/**
	 * Texture V at the ribbon's lower edge (world -Y) and upper edge (+Y). Vanilla
	 * beam uses a horizontal strip
	 * of the sheet; a square custom PNG often uses {@code 1f} and {@code 0f} or
	 * {@code 0f}/{@code 1f} for full height.
	 */
	/** Full tile height (flip if the art appears upside down). */
	private static final float V_AT_RIBBON_BOTTOM = 1.0F;
	private static final float V_AT_RIBBON_TOP = 0.0F;
	/**
	 * Rotation speed around vertical (Y): degrees added per <em>game tick</em> (20
	 * ticks ≈ 1 second).
	 * Must match in {@link #tick} and {@link #render} (via {@code partialTicks}) so
	 * motion is smooth.
	 */
	private static final float YAW_DEGREES_PER_TICK = 2f;

	/**
	 * Peak vertical displacement of the ribbon centerline (blocks). 0 = flat ring.
	 */
	private static final float VERTICAL_WAVE_AMPLITUDE = 0.6F;
	/**
	 * How many full sine periods fit around the 360° ring (e.g. 4 = four ups and
	 * downs
	 * around the circle).
	 */
	private static final float VERTICAL_WAVE_CYCLES_AROUND_RING = 3f;
	/**
	 * If non-zero, the wave pattern travels around the ring (rad/tick added to sin
	 * phase).
	 * 0 = standing wave in ring-fixed space (still rotates with
	 * {@link #YAW_DEGREES_PER_TICK}).
	 */
	private static final float VERTICAL_WAVE_PHASE_RAD_PER_TICK = 0.08F;

	private final int targetEntityId;
	private final double ringRadius;
	private final double yOffset;
	private float yawDeg;

	GmaxForceFieldRingParticle(
			ClientLevel level,
			double x,
			double y,
			double z,
			double ringRadius,
			double entityIdParam,
			double yOffset) {
		super(level, x, y, z);
		this.ringRadius = ringRadius;
		// From packet: maxSpeed * yDist; entity id sent as float round-trip (not
		// longBits — packet is float).
		this.targetEntityId = (int) Math.round(entityIdParam);
		this.yOffset = yOffset;
		this.hasPhysics = false;
		this.lifetime = 6000;
		this.yawDeg = 0f;
		this.rCol = 1f;
		this.gCol = 1f;
		this.bCol = 1f;
		this.alpha = 0.88f;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.CUSTOM;
	}

	@Override
	public void tick() {
		super.tick();
		this.yawDeg += YAW_DEGREES_PER_TICK;
		if (this.targetEntityId == 0) {
			return;
		}
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
		this.y = e.getY() + this.yOffset;
		this.z = e.getZ();
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		// ParticleRenderType.CUSTOM.begin() disables blend; beam texture needs alpha
		// blending or it is invisible.
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderTexture(0, RING_TEXTURE);
		Vec3Helper cam = Vec3Helper.of(camera.getPosition());
		float px = (float) Mth.lerp(partialTicks, this.xo, this.x);
		float py = (float) Mth.lerp(partialTicks, this.yo, this.y);
		float pz = (float) Mth.lerp(partialTicks, this.zo, this.z);
		float cx = px - cam.x;
		float cy = py - cam.y;
		float cz = pz - cam.z;
		float r = (float) this.ringRadius;
		float yawRad = (float) Math.toRadians(this.yawDeg + partialTicks * YAW_DEGREES_PER_TICK);
		Quaternionf rot = new Quaternionf().rotateY(yawRad);
		float wavePhase = (this.age + partialTicks) * VERTICAL_WAVE_PHASE_RAD_PER_TICK;
		float halfH = QUAD_HEIGHT * 0.5f;

		for (int i = 0; i < SEGMENTS; i++) {
			double t0 = (i / (double) SEGMENTS) * Math.PI * 2;
			double t1 = ((i + 1) / (double) SEGMENTS) * Math.PI * 2;
			float w0 = verticalWaveOffset(t0, wavePhase);
			float w1 = verticalWaveOffset(t1, wavePhase);
			Vector3f p0 = new Vector3f((float) (Math.cos(t0) * r), w0 - halfH, (float) (Math.sin(t0) * r));
			Vector3f p1 = new Vector3f((float) (Math.cos(t1) * r), w1 - halfH, (float) (Math.sin(t1) * r));
			Vector3f p2 = new Vector3f((float) (Math.cos(t1) * r), w1 + halfH, (float) (Math.sin(t1) * r));
			Vector3f p3 = new Vector3f((float) (Math.cos(t0) * r), w0 + halfH, (float) (Math.sin(t0) * r));
			rot.transform(p0);
			rot.transform(p1);
			rot.transform(p2);
			rot.transform(p3);
			int localSeg = i % SUBDIVS_PER_TILE;
			float u0 = (float) localSeg / SUBDIVS_PER_TILE;
			float u1 = (float) (localSeg + 1) / SUBDIVS_PER_TILE;
			// Two windings: GL cull state may not apply at draw time (batched mesh).
			// Duplicate quads so both sides are front-facing.
			putQuad(buffer, cx, cy, cz, p0, p1, p2, p3, u0, u1);
			putQuadReversedWinding(buffer, cx, cy, cz, p0, p1, p2, p3, u0, u1);
		}
	}

	/**
	 * Local-space Y offset for angle θ (rad) on the horizontal ring: circle in XZ,
	 * sine in Y.
	 */
	private static float verticalWaveOffset(double angleRad, float phaseRad) {
		return VERTICAL_WAVE_AMPLITUDE
				* Mth.sin((float) (VERTICAL_WAVE_CYCLES_AROUND_RING * angleRad + phaseRad));
	}

	private static void putQuad(
			VertexConsumer buffer,
			float cx,
			float cy,
			float cz,
			Vector3f p0,
			Vector3f p1,
			Vector3f p2,
			Vector3f p3,
			float u0,
			float u1) {
		int light = 0xF000F0;
		float r = 1f, g = 1f, b = 1f, a = 0.88f;
		buffer.addVertex(cx + p0.x, cy + p0.y, cz + p0.z).setUv(u0, V_AT_RIBBON_BOTTOM).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p1.x, cy + p1.y, cz + p1.z).setUv(u1, V_AT_RIBBON_BOTTOM).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p2.x, cy + p2.y, cz + p2.z).setUv(u1, V_AT_RIBBON_TOP).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p3.x, cy + p3.y, cz + p3.z).setUv(u0, V_AT_RIBBON_TOP).setColor(r, g, b, a)
				.setLight(light);
	}

	/**
	 * Same four corners as [putQuad] but opposite triangle winding so the other
	 * side survives backface culling.
	 */
	private static void putQuadReversedWinding(
			VertexConsumer buffer,
			float cx,
			float cy,
			float cz,
			Vector3f p0,
			Vector3f p1,
			Vector3f p2,
			Vector3f p3,
			float u0,
			float u1) {
		int light = 0xF000F0;
		float r = 1f, g = 1f, b = 1f, a = 0.88f;
		buffer.addVertex(cx + p0.x, cy + p0.y, cz + p0.z).setUv(u0, V_AT_RIBBON_BOTTOM).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p3.x, cy + p3.y, cz + p3.z).setUv(u0, V_AT_RIBBON_TOP).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p2.x, cy + p2.y, cz + p2.z).setUv(u1, V_AT_RIBBON_TOP).setColor(r, g, b, a)
				.setLight(light);
		buffer.addVertex(cx + p1.x, cy + p1.y, cz + p1.z).setUv(u1, V_AT_RIBBON_BOTTOM).setColor(r, g, b, a)
				.setLight(light);
	}

	private record Vec3Helper(float x, float y, float z) {
		static Vec3Helper of(net.minecraft.world.phys.Vec3 v) {
			return new Vec3Helper((float) v.x, (float) v.y, (float) v.z);
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
			return new GmaxForceFieldRingParticle(level, x, y, z, xd, yd, zd);
		}
	}
}
