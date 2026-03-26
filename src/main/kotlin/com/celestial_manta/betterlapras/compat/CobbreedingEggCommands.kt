package com.celestial_manta.betterlapras.compat

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Species as CobblemonSpecies
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import ludichat.cobbreeding.Cobbreeding
import ludichat.cobbreeding.EggUtilities
import ludichat.cobbreeding.PokemonEgg
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Commands that use [EggUtilities] from Cobbreeding. This type is only referenced from [com.celestial_manta.betterlapras.BetterLapras]
 * when the `cobbreeding` mod is loaded, so missing Cobbreeding does not pull Cobbreeding classes at runtime.
 *
 * Egg payloads use the **egg_info** component (AES-128 key in [Cobbreeding.ENCRYPTION_KEY_PATH], decrypted via [EggUtilities.decrypt]).
 */
object CobbreedingEggCommands {
	private val log = LoggerFactory.getLogger("betterlapras")

	/** Matches decrypted Pokémon spec text (Cobbreeding encrypts `asString(" ")`). */
	private const val SHINY_SUBSTRING = "shiny=true"

	private val IV_SCAN_LITERALS = listOf("1x31", "2x31", "3x31", "4x31", "5x31", "6x31")

	private const val LAPRAS_SHOWDOWN_ID = "lapras"

	private val laprasPartyRequiredMessage = Component.literal("You need a Lapras in your party to use this command")

	fun register() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			dispatcher.register(
				Commands.literal("lapras")
					.then(
						Commands.literal("egg")
							.then(
								Commands.literal("info")
									.executes { ctx -> eggInfo(ctx) },
							)
							.then(buildScanBranch()),
					),
			)
		}
	}

	private fun buildScanBranch(): LiteralArgumentBuilder<CommandSourceStack> {
		@Suppress("VariableAssignmentWithWrongType")
		var scan: LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("scan")
			.then(
				Commands.literal("shiny")
					.executes { ctx -> eggScan(ctx, null, EggScanMode.Shiny) }
					.then(
						Commands.argument("pos", BlockPosArgument.blockPos())
							.executes { ctx ->
								eggScan(ctx, BlockPosArgument.getBlockPos(ctx, "pos"), EggScanMode.Shiny)
							},
					),
			)
		for (lit in IV_SCAN_LITERALS) {
			val minPerfect = lit.removeSuffix("x31").toInt()
			scan = scan.then(
				Commands.literal(lit)
					.executes { ctx -> eggScan(ctx, null, EggScanMode.MinPerfectIvs(minPerfect)) }
					.then(
						Commands.argument("pos", BlockPosArgument.blockPos())
							.executes { ctx ->
								eggScan(
									ctx,
									BlockPosArgument.getBlockPos(ctx, "pos"),
									EggScanMode.MinPerfectIvs(minPerfect),
								)
							},
					),
			)
		}
		scan = scan.then(
			Commands.literal("nature")
				.then(
					Commands.argument("name", StringArgumentType.word())
						.executes { ctx ->
							val name = StringArgumentType.getString(ctx, "name")
							eggScan(ctx, null, EggScanMode.Nature(name))
						}
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.executes { ctx ->
									val name = StringArgumentType.getString(ctx, "name")
									eggScan(ctx, BlockPosArgument.getBlockPos(ctx, "pos"), EggScanMode.Nature(name))
								},
						),
				),
		)
		return scan
	}

	private sealed class EggScanMode {
		data object Shiny : EggScanMode()

		data class MinPerfectIvs(val minCount: Int) : EggScanMode()

		data class Nature(val name: String) : EggScanMode()
	}

	private fun eggInfo(ctx: CommandContext<CommandSourceStack>): Int {
		val source = ctx.source
		val player = source.player
		if (player == null) {
			source.sendFailure(Component.literal("This command must be run by a player."))
			return 0
		}
		if (!player.hasLaprasInParty()) {
			source.sendFailure(laprasPartyRequiredMessage)
			return 0
		}
		val stack = player.mainHandItem
		if (stack.item !is PokemonEgg) {
			source.sendFailure(Component.literal("Hold a Cobbreeding Pokémon egg in the main hand."))
			return 0
		}
		val eggInfo = stack.get(PokemonEgg.EGG_INFO)
		if (eggInfo.isNullOrBlank()) {
			source.sendFailure(Component.literal("This egg has no cobbreeding:egg_info data."))
			return 0
		}
		val props =
			try {
				EggUtilities.decrypt(eggInfo)
			} catch (e: Exception) {
				log.error(
					"Lapras /lapras egg info: failed to decrypt egg_info (AES key file: {}).",
					Cobbreeding.ENCRYPTION_KEY_PATH,
					e,
				)
				source.sendFailure(Component.literal("Could not decrypt egg_info. Check the server log and ${Cobbreeding.ENCRYPTION_KEY_PATH}."))
				return 0
			}
		log.info(
			"Lapras /lapras egg info: player={} keyFile={} decrypted={}",
			player.gameProfile.name,
			Cobbreeding.ENCRYPTION_KEY_PATH,
			props.originalString,
		)
		source.sendSuccess({ Component.literal(describeProperties(props)) }, false)
		return 1
	}

	private fun eggScan(ctx: CommandContext<CommandSourceStack>, pos: BlockPos?, mode: EggScanMode): Int {
		val source = ctx.source
		val player = source.player
		if (player == null) {
			source.sendFailure(Component.literal("This command must be run by a player."))
			return 0
		}
		if (!player.hasLaprasInParty()) {
			source.sendFailure(laprasPartyRequiredMessage)
			return 0
		}
		val level = source.level
		if (level !is ServerLevel) {
			source.sendFailure(Component.literal("This command must run on the server."))
			return 0
		}
		val lines = ArrayList<String>()
		val scanLog = ArrayList<String>()
		val visitSlot =
			fun(slot: Int, stack: ItemStack) {
				if (stack.item !is PokemonEgg) return
				val props = tryDecryptProps(stack) ?: return
				val speciesName = speciesDisplayName(props)
				when (mode) {
					is EggScanMode.Shiny -> {
						if (isShinyProps(props)) {
							lines.add(formatLineSimple(slot, speciesName, "shiny"))
							scanLog.add("slot=$slot:$speciesName:shiny")
						}
					}
					is EggScanMode.MinPerfectIvs -> {
						val n = countPerfectIvs(props)
						if (n >= mode.minCount) {
							val shiny = isShinyProps(props)
							lines.add(formatLineIx31(slot, speciesName, n, shiny))
							scanLog.add(
								buildString {
									append("slot=$slot:$speciesName:${n}x31")
									if (shiny) append(":shiny")
								},
							)
						}
					}
					is EggScanMode.Nature -> {
						if (matchesNature(props, mode.name)) {
							val nat = props.nature!!.trim()
							lines.add(formatLineNature(slot, speciesName, nat))
							scanLog.add("slot=$slot:$speciesName:nature=$nat")
						}
					}
				}
			}
		if (pos == null) {
			val inv = player.inventory
			for (i in 0 until inv.containerSize) {
				visitSlot(i, inv.getItem(i))
			}
		} else {
			val be = level.getBlockEntity(pos)
			val container = be as? Container ?: run {
				source.sendFailure(Component.literal("That block is not a storage container."))
				return 0
			}
			for (i in 0 until container.containerSize) {
				visitSlot(i, container.getItem(i))
			}
		}
		val summary =
			if (lines.isEmpty()) {
				"No matching Pokémon eggs (decrypted egg_info, key: ${Cobbreeding.ENCRYPTION_KEY_PATH})."
			} else {
				lines.joinToString("\n")
			}
		log.info(
			"Lapras /lapras egg scan {}: player={} keyFile={} matches={} detail={}",
			mode::class.simpleName,
			player.gameProfile.name,
			Cobbreeding.ENCRYPTION_KEY_PATH,
			lines.size,
			scanLog.joinToString("; "),
		)
		source.sendSuccess({ Component.literal(summary) }, false)
		return 1
	}

	private fun formatLineSimple(slot: Int, speciesLabel: String, scanType: String): String =
		"Slot $slot: $speciesLabel egg is $scanType"

	private fun formatLineIx31(slot: Int, speciesLabel: String, count: Int, shiny: Boolean): String {
		val base = "Slot $slot: $speciesLabel egg is ${count}x31"
		return if (shiny) "$base, Shiny" else base
	}

	private fun formatLineNature(slot: Int, speciesLabel: String, natureQuery: String): String =
		"Slot $slot: $speciesLabel egg is nature $natureQuery"

	private fun tryDecryptProps(stack: ItemStack): PokemonProperties? {
		val eggInfo = stack.get(PokemonEgg.EGG_INFO) ?: return null
		return try {
			EggUtilities.decrypt(eggInfo)
		} catch (_: Exception) {
			null
		}
	}

	private fun speciesDisplayName(props: PokemonProperties): String {
		val raw = props.species?.takeIf { it.isNotBlank() } ?: return "Unknown"
		val species: CobblemonSpecies? = PokemonSpecies.getByName(raw)
		return species?.translatedName?.string ?: raw.replaceFirstChar { it.titlecase() }
	}

	private fun isShinyProps(props: PokemonProperties): Boolean {
		if (props.shiny == true) return true
		return props.originalString.contains(SHINY_SUBSTRING)
	}

	private fun countPerfectIvs(props: PokemonProperties): Int {
		val ivs = props.ivs ?: return 0
		return Stats.PERMANENT.count { stat -> ivs.getOrDefault(stat) == 31 }
	}

	private fun matchesNature(props: PokemonProperties, query: String): Boolean {
		val n = props.nature?.trim()?.takeIf { it.isNotEmpty() } ?: return false
		return n.equals(query, ignoreCase = true)
	}

	private fun ServerPlayer.hasLaprasInParty(): Boolean {
		val party = Cobblemon.storage.getParty(this)
		for (i in 0 until party.size()) {
			val pokemon = party.get(i) ?: continue
			if (pokemon.species.showdownId().equals(LAPRAS_SHOWDOWN_ID, ignoreCase = true)) {
				return true
			}
		}
		return false
	}

	private fun describeProperties(props: PokemonProperties): String {
		val lines = ArrayList<String>()
		props.species?.takeIf { it.isNotBlank() }?.let { lines.add("Species: $it") }
		props.form?.takeIf { it.isNotBlank() }?.let { lines.add("Form: $it") }
		props.shiny?.let { lines.add("Shiny: $it") }
		props.nature?.takeIf { it.isNotBlank() }?.let { lines.add("Nature: $it") }
		props.ability?.takeIf { it.isNotBlank() }?.let { lines.add("Ability: $it") }
		props.pokeball?.takeIf { it.isNotBlank() }?.let { lines.add("Poké Ball: $it") }
		val moves = props.moves
		if (moves != null && moves.isNotEmpty()) {
			lines.add("Moves: ${moves.joinToString(", ")}")
		}
		props.originalString.takeIf { it.isNotBlank() }?.let { lines.add("Full spec: $it") }
		return if (lines.isEmpty()) {
			"(No species data on this egg.)"
		} else {
			lines.joinToString("\n")
		}
	}
}
