package com.celestial_manta.betterlapras

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/**
 * [SoundEvent]s must be registered on [BuiltInRegistries.SOUND_EVENT]; [sounds.json] alone does not
 * add entries, so [net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT] lookups return null.
 */
object BetterLaprasSounds {
	val PERISH_SONG: SoundEvent = SoundEvent.createVariableRangeEvent(
		ResourceLocation.fromNamespaceAndPath("betterlapras", "perishsong"),
	)

	fun register() {
		val id = PERISH_SONG.location
		net.minecraft.core.Registry.register(BuiltInRegistries.SOUND_EVENT, id, PERISH_SONG)
	}
}
