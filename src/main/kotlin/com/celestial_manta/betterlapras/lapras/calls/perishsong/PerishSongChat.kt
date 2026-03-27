package com.celestial_manta.betterlapras.lapras.calls.perishsong

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object PerishSongChat {
	fun normalizePhrase(s: String): String =
		s.trim().lowercase().replace(Regex("\\s+"), " ")

	/** True if the normalized chat line contains both "lapras" and "perish song" (same message). */
	fun matchesPerishSongChat(normalized: String): Boolean =
		normalized.contains("lapras") && normalized.contains("perish song")

	fun isOnCooldown(now: Int, lastTriggerTick: Int?): Boolean {
		val last = lastTriggerTick ?: -PerishSongConfig.COOLDOWN_TICKS * 2
		return now - last < PerishSongConfig.COOLDOWN_TICKS
	}

	/**
	 * Player-only action bar when Perish Song is on cooldown; uses nearest owned Lapras name when available.
	 */
	fun sendNoEnergyMessage(sender: ServerPlayer) {
		val lapras = PerishSongTargeting.findNearestOwnedLapras(sender)
		val text = if (lapras != null) {
			Component.literal("")
				.append(lapras.displayName ?: Component.literal("Lapras"))
				.append(Component.literal(": No energy to use move."))
		} else {
			Component.literal("No energy to use move.")
		}
		sender.displayClientMessage(text, true)
	}
}
