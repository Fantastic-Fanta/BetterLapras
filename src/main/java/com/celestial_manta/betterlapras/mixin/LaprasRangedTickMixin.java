package com.celestial_manta.betterlapras.mixin;

import com.celestial_manta.betterlapras.LaprasProjectileCombat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonEntity.class)
public class LaprasRangedTickMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void betterlapras$laprasRangedTick(CallbackInfo ci) {
		LaprasProjectileCombat.tickRangedVolley((PokemonEntity) (Object) this);
	}
}
