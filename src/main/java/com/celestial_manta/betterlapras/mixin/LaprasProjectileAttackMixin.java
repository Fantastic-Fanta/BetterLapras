package com.celestial_manta.betterlapras.mixin;

import com.celestial_manta.betterlapras.LaprasMoveCombat;
import com.celestial_manta.betterlapras.LaprasSlot1SupportMoves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public class LaprasProjectileAttackMixin {

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void betterlapras$laprasWaterGunInsteadOfMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
		PokemonEntity self = (PokemonEntity) (Object) this;
		if (LaprasSlot1SupportMoves.suppressesCombat(self)) {
			cir.setReturnValue(false);
			return;
		}
		if (LaprasMoveCombat.tryReplaceMeleeWithProjectile(self, target)) {
			cir.setReturnValue(true);
		}
	}
}
