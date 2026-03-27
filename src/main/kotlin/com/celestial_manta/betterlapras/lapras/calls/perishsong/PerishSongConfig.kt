package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType

/**
 * Tunable parameters for Perish Song. Edit values here without touching handler logic.
 */
object PerishSongConfig {
	const val TPS = 20

	/** Initial mark and ongoing range check: leave this cylinder/sphere and you drop off the list. */
	const val MARK_RADIUS = 50.0
	val markRadiusSq: Double = MARK_RADIUS * MARK_RADIUS

	/** Warnings at 0s, 10s, 20s (each applies darkness to marked targets); resolve at 30s. */
	val eventOffsetsTicks: IntArray = intArrayOf(0, 10 * TPS, 20 * TPS, 30 * TPS)

	/** 20 minutes between Perish Song uses per player. */
	const val COOLDOWN_TICKS = 20 * 60 * TPS

	const val SEARCH_PAD = 256.0

	/** Third milestone (index 2): nausea V from here… */
	const val THIRD_WARNING_TICKS = 20 * TPS

	/** …until this tick, then nausea X for marked entities until the end. */
	val NAUSEA_TIER2_START_TICKS: Int = THIRD_WARNING_TICKS + 5 * TPS

	/** Reapply duration so effects don’t flicker between ticks. */
	const val EFFECT_REFRESH_TICKS = 6 * TPS

	/** Darkness duration per warning milestone (~3 s). */
	const val DARKNESS_WARNING_TICKS = 3 * TPS

	/** End rod rings around focal Lapras during Perish Song (XZ circle, Y = transverse displacement). */
	const val PERISH_RING_RADIUS_INNER = 2.25
	const val PERISH_RING_RADIUS_OUTER = 3.2
	const val PERISH_RING_SEGMENTS = 48
	const val PERISH_WAVE_PEAKS_AROUND_RING = 4.0
	const val PERISH_WAVE_AMP_BLOCKS = 0.42
	const val PERISH_RING_Y_OFFSET = 1.05

	/** Phase advance per game tick (wave propagation around the ring). */
	const val PERISH_WAVE_PHASE_PER_TICK = 0.18

	/** Occasional floaty notes around Lapras (not every tick). */
	const val NOTE_SPAWN_INTERVAL_TICKS = 14
	const val NOTE_SPAWN_COUNT = 3
	const val NOTE_SPAWN_RADIUS_MIN = 2.0
	const val NOTE_SPAWN_RADIUS_MAX = 3.6
	const val NOTE_SPAWN_Y_BASE = 1.0
	const val NOTE_SPAWN_Y_JITTER = 0.5

	const val DEATH_BURST_PARTICLES = 36
	const val DEATH_BURST_SPREAD = 0.9

	/** Extra entity types that count as hostile for Perish Song (datapack-extendable). */
	val HOSTILE_ENTITY_TAG: TagKey<EntityType<*>> = TagKey.create(
		Registries.ENTITY_TYPE,
		ResourceLocation.fromNamespaceAndPath("betterlapras", "hostile"),
	)
}
