package com.celestial_manta.betterlapras

import com.celestial_manta.betterlapras.lapras.calls.perishsong.FloatyNoteParticle
import com.celestial_manta.betterlapras.lapras.calls.perishsong.PerishSongEndRodParticle
import com.celestial_manta.betterlapras.lapras.gmax_cosmetics.GmaxCloudClusterParticle
import com.celestial_manta.betterlapras.lapras.gmax_cosmetics.GmaxForceFieldRingParticle
import com.celestial_manta.betterlapras.lapras.moves.projectile.LaprasMoveProjectileRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import org.slf4j.LoggerFactory

object BetterLaprasClient : ClientModInitializer {
	private val logger = LoggerFactory.getLogger("betterlapras")

	override fun onInitializeClient() {
		ParticleFactoryRegistry.getInstance().register(
			BetterLaprasParticles.PERISH_SONG_END_ROD,
		) { sprites: FabricSpriteProvider ->
			PerishSongEndRodParticle.Provider(sprites)
		}
		ParticleFactoryRegistry.getInstance().register(
			BetterLaprasParticles.FLOATY_NOTE,
		) { sprites: FabricSpriteProvider ->
			FloatyNoteParticle.Provider(sprites)
		}
		ParticleFactoryRegistry.getInstance().register(
			BetterLaprasParticles.GMAX_FORCE_FIELD_RING,
		) { sprites: FabricSpriteProvider ->
			GmaxForceFieldRingParticle.Provider(sprites)
		}
		ParticleFactoryRegistry.getInstance().register(
			BetterLaprasParticles.GMAX_CLOUD_CLUSTERS,
		) { sprites: FabricSpriteProvider ->
			GmaxCloudClusterParticle.Provider(sprites)
		}
		EntityRendererRegistry.register(BetterLaprasEntities.LAPRAS_MOVE_PROJECTILE, ::LaprasMoveProjectileRenderer)
		logger.info("BetterLapras client initialized")
	}
}
