package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.combat.SwingSpeed;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void modifyHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        double mul = SwingSpeed.getMultiplier();
        if (mul != 1.0) {
            cir.setReturnValue(Math.max(1, (int)(cir.getReturnValue() / mul)));
        }
    }
}
