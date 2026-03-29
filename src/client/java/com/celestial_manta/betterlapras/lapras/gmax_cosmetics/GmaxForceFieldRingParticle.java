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
import net.minecraft.core.particles.ParticleTypes;
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
 * <b>“3D particles” in Minecraft:</b> vanilla particles are billboards on the
 * particle sheet. True 3D is done with {@link ParticleRenderType#CUSTOM} and
 * emitting vertices in {@link #render} (same pipeline as this class). Here,
 * small
 * ice octahedra are drawn as triangles (emitted as degenerate quads for the
 * particle buffer) and orbit the ring.
 * <p>
 * <b>Textures (one bind, horizontal atlas):</b>
 * {@link ParticleRenderType#CUSTOM}
 * batches all vertices into one draw, so changing {@code setShaderTexture} mid-
 * {@code render} does not give the ring and shards different samplers — the
 * last
 * bind wins for the whole batch. This class uses a single texture: put your
 * ring
 * art in the <em>left</em> half (U 0–0.5) and an ice tile (e.g. pasted vanilla
 * ice) in the <em>right</em> half (U 0.5–1). Ring UVs are scaled to the left
 * half; shard triangles sample the center of the right half.
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
 * <p>
 * Vanilla {@link ParticleTypes#END_ROD} clusters spawn occasionally around the
 * ring
 * ({@link #END_ROD_CLUSTER_RARITY_TICKS}).
 */
@Environment(EnvType.CLIENT)
public final class GmaxForceFieldRingParticle extends Particle {
	/**
	 * Horizontal atlas: left half = ring ribbon, right half = ice tile for shards
	 * ({@link #RING_ATLAS_U_SCALE}, {@link #ICE_ATLAS_U_CENTER}).
	 */
	private static final ResourceLocation RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			"betterlapras",
			"textures/particle/gmax_force_field_ring.png");
	/**
	 * Ring uses U in [0, {@value #RING_ATLAS_U_SCALE}) of the atlas (left half).
	 */
	private static final float RING_ATLAS_U_SCALE = 0.5F;
	/** Ice octahedron faces sample near the center of the atlas right half. */
	private static final float ICE_ATLAS_U_CENTER = 0.75F;
	private static final float ICE_ATLAS_V_CENTER = 0.5F;
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

	/**
	 * ~1 / this chance per tick to spawn one small end-rod cluster around the ring.
	 */
	private static final int END_ROD_CLUSTER_RARITY_TICKS = 8;

	/** How many octahedra orbit the ring. */
	private static final int ICE_DIAMOND_COUNT = 6;
	/**
	 * Per-shard radius (distance from center to axis tip); picked in ctor in
	 * [{@link #ICE_DIAMOND_SCALE_MIN}, {@link #ICE_DIAMOND_SCALE_MAX}].
	 */
	private static final float ICE_DIAMOND_SCALE_MIN = 0.2F;
	private static final float ICE_DIAMOND_SCALE_MAX = 0.5F;
	/**
	 * Orbit radius as a fraction of the ring radius (slightly inside the ribbon).
	 */
	private static final float ICE_ORBIT_RADIUS_FACTOR = 1.02F;
	/** Orbital drift around the ring (rad/tick). */
	private static final float ICE_ORBIT_PHASE_RAD_PER_TICK = 0.014F;
	/** Extra vertical bob (blocks). */
	private static final float ICE_BOB_AMP = 0.12F;
	/** Self-spin of each crystal (rad/tick). */
	private static final float ICE_SPIN_RAD_PER_TICK = 0.03F;

	/** Octahedron vertices (±1,0,0), (0,±1,0), (0,0,±1) — scaled in mesh. */
	private static final Vector3f[] OCTA_VERTS = {
			new Vector3f(1f, 0f, 0f), new Vector3f(-1f, 0f, 0f),
			new Vector3f(0f, 1f, 0f), new Vector3f(0f, -1f, 0f),
			new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, -1f),
	};
	/** Eight triangular faces (vertex indices). */
	private static final int[][] OCTA_FACES = {
			{ 0, 2, 4 }, { 0, 2, 5 }, { 0, 3, 4 }, { 0, 3, 5 },
			{ 1, 2, 4 }, { 1, 2, 5 }, { 1, 3, 4 }, { 1, 3, 5 },
	};

	private final int targetEntityId;
	private final double ringRadius;
	private final double yOffset;
	private final float[] iceDiamondScales;
	private float yawDeg;
	/** Advances ice-diamond orbit around the ring (rad). */
	private float iceOrbitPhase;

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
		this.iceDiamondScales = new float[ICE_DIAMOND_COUNT];
		float span = ICE_DIAMOND_SCALE_MAX - ICE_DIAMOND_SCALE_MIN;
		for (int i = 0; i < ICE_DIAMOND_COUNT; i++) {
			this.iceDiamondScales[i] = ICE_DIAMOND_SCALE_MIN + this.random.nextFloat() * span;
		}
		this.hasPhysics = false;
		this.lifetime = 6000;
		this.yawDeg = 0f;
		this.iceOrbitPhase = 0f;
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
		this.iceOrbitPhase += ICE_ORBIT_PHASE_RAD_PER_TICK;
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
			this.y = e.getY() + this.yOffset;
			this.z = e.getZ();
		}
		maybeSpawnEndRodSparkles();
	}

	/**
	 * Occasional vanilla end-rod bursts on/near the ring (same radius, vertical
	 * wave, and yaw as the mesh).
	 */
	private void maybeSpawnEndRodSparkles() {
		if (this.random.nextInt(END_ROD_CLUSTER_RARITY_TICKS) != 0) {
			return;
		}
		float wavePhase = this.age * VERTICAL_WAVE_PHASE_RAD_PER_TICK;
		float yawRad = (float) Math.toRadians(this.yawDeg);
		Quaternionf rot = new Quaternionf().rotateY(yawRad);
		int count = 2 + this.random.nextInt(4);
		double rr = this.ringRadius;
		for (int k = 0; k < count; k++) {
			double theta = this.random.nextDouble() * Math.PI * 2;
			double radJitter = rr * (0.94 + 0.12 * this.random.nextDouble());
			float waveY = verticalWaveOffset(theta, wavePhase);
			waveY += (float) ((this.random.nextDouble() - 0.5) * 0.35 * QUAD_HEIGHT);
			Vector3f local = new Vector3f(
					(float) (Math.cos(theta) * radJitter),
					waveY + (float) ((this.random.nextDouble() - 0.5) * 0.12),
					(float) (Math.sin(theta) * radJitter));
			rot.transform(local);
			double wx = this.x + local.x;
			double wy = this.y + local.y;
			double wz = this.z + local.z;
			double vx = (this.random.nextDouble() - 0.5) * 0.045;
			double vy = this.random.nextDouble() * 0.07;
			double vz = (this.random.nextDouble() - 0.5) * 0.045;
			this.level.addParticle(ParticleTypes.END_ROD, wx, wy, wz, vx, vy, vz);
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		// ParticleRenderType.CUSTOM.begin() disables blend; beam texture needs alpha
		// blending or it is invisible.
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		// One texture bind: ring + ice shards share atlas on RING_TEXTURE (see class
		// Javadoc).
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
			float u0 = RING_ATLAS_U_SCALE * (float) localSeg / SUBDIVS_PER_TILE;
			float u1 = RING_ATLAS_U_SCALE * (float) (localSeg + 1) / SUBDIVS_PER_TILE;
			// Two windings: GL cull state may not apply at draw time (batched mesh).
			// Duplicate quads so both sides are front-facing.
			putQuad(buffer, cx, cy, cz, p0, p1, p2, p3, u0, u1);
			putQuadReversedWinding(buffer, cx, cy, cz, p0, p1, p2, p3, u0, u1);
		}

		float orbitPhase = this.iceOrbitPhase + partialTicks * ICE_ORBIT_PHASE_RAD_PER_TICK;
		float spinAngle = (this.age + partialTicks) * ICE_SPIN_RAD_PER_TICK;
		renderIceDiamonds(buffer, cx, cy, cz, r, rot, wavePhase, orbitPhase, spinAngle, partialTicks);
	}

	/**
	 * Ice octahedra on a slightly smaller horizontal orbit, with slow spin and bob.
	 */
	private void renderIceDiamonds(
			VertexConsumer buffer,
			float cx,
			float cy,
			float cz,
			float ringR,
			Quaternionf ringYawRot,
			float wavePhase,
			float orbitPhase,
			float spinAngle,
			float partialTicks) {
		float orbitR = ringR * ICE_ORBIT_RADIUS_FACTOR;
		float bobT = (this.age + partialTicks) * 0.07F;
		for (int d = 0; d < ICE_DIAMOND_COUNT; d++) {
			double theta = orbitPhase + d * (Math.PI * 2 / ICE_DIAMOND_COUNT);
			float waveY = verticalWaveOffset(theta, wavePhase);
			float bob = Mth.sin(bobT + d * 1.7F) * ICE_BOB_AMP;
			Vector3f orbit = new Vector3f(
					(float) (Math.cos(theta) * orbitR),
					waveY * 0.35F + bob,
					(float) (Math.sin(theta) * orbitR));
			Quaternionf spin = new Quaternionf()
					.rotateY(spinAngle + d * 0.9F)
					.rotateX(Mth.sin(spinAngle * 0.7F + d) * 0.35F);
			float shardScale = this.iceDiamondScales[d];
			for (int[] face : OCTA_FACES) {
				Vector3f a = new Vector3f(OCTA_VERTS[face[0]]).mul(shardScale);
				Vector3f b = new Vector3f(OCTA_VERTS[face[1]]).mul(shardScale);
				Vector3f c = new Vector3f(OCTA_VERTS[face[2]]).mul(shardScale);
				spin.transform(a);
				spin.transform(b);
				spin.transform(c);
				a.add(orbit);
				b.add(orbit);
				c.add(orbit);
				ringYawRot.transform(a);
				ringYawRot.transform(b);
				ringYawRot.transform(c);
				putTriangleDoubleSided(buffer, cx, cy, cz, a, b, c);
			}
		}
	}

	/**
	 * Particle batch uses quad mode; emit a triangle as a degenerate quad (fourth
	 * vertex repeats the third).
	 */
	private static void putTriangleDoubleSided(
			VertexConsumer buffer,
			float cx,
			float cy,
			float cz,
			Vector3f a,
			Vector3f b,
			Vector3f c) {
		putTriangleQuad(buffer, cx, cy, cz, a, b, c);
		putTriangleQuad(buffer, cx, cy, cz, a, c, b);
	}

	private static void putTriangleQuad(
			VertexConsumer buffer,
			float cx,
			float cy,
			float cz,
			Vector3f a,
			Vector3f b,
			Vector3f c) {
		int light = 0xF000F0;
		float cr = 1f, cg = 1f, cb = 1f, ca = 0.96f;
		float u = ICE_ATLAS_U_CENTER;
		float v = ICE_ATLAS_V_CENTER;
		buffer.addVertex(cx + a.x, cy + a.y, cz + a.z).setUv(u, v).setColor(cr, cg, cb, ca).setLight(light);
		buffer.addVertex(cx + b.x, cy + b.y, cz + b.z).setUv(u, v).setColor(cr, cg, cb, ca).setLight(light);
		buffer.addVertex(cx + c.x, cy + c.y, cz + c.z).setUv(u, v).setColor(cr, cg, cb, ca).setLight(light);
		buffer.addVertex(cx + c.x, cy + c.y, cz + c.z).setUv(u, v).setColor(cr, cg, cb, ca).setLight(light);
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
		float r = 1f, g = 1f, b = 1f, a = 0.95f;
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
