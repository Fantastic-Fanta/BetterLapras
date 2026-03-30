package com.celestial_manta.betterlapras.lapras.gmax_cosmetics

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import kotlin.jvm.JvmStatic

/**
 * When Lapras has G-Max factor, adds only that entity to the same scoreboard team Mega Showdown uses
 * for dynamax (`glow_dynamax_red`). Does not set the entity glowing tag.
 */
object LaprasGmaxDynamaxGlow {
	private const val TEAM_NAME = "glow_dynamax_red"

	@JvmStatic
	fun tick(entity: PokemonEntity) {
		val level = entity.level()
		if (level !is ServerLevel) return
		val pokemon = entity.pokemon
		if (!pokemon.species.name.equals("lapras", ignoreCase = true)) return

		if (pokemon.gmaxFactor) {
			applyRedDynamaxTeam(entity, level)
		} else {
			clearRedTeamIfMember(entity, level)
		}
	}

	private fun applyRedDynamaxTeam(entity: PokemonEntity, level: ServerLevel) {
		val scoreboard = level.scoreboard
		var team = scoreboard.getPlayerTeam(TEAM_NAME)
		if (team == null) {
			team = scoreboard.addPlayerTeam(TEAM_NAME)
			team.color = ChatFormatting.RED
		}
		scoreboard.addPlayerToTeam(entity.scoreboardName, team)
	}

	private fun clearRedTeamIfMember(entity: PokemonEntity, level: ServerLevel) {
		val scoreboard = level.scoreboard
		val team = scoreboard.getPlayerTeam(TEAM_NAME) ?: return
		val name = entity.scoreboardName
		if (scoreboard.getPlayersTeam(name) != team) return
		scoreboard.removePlayerFromTeam(name)
	}
}
