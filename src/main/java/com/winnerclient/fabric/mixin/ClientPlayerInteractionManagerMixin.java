package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.combat.Criticals;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        var mod = ModuleManager.getModule("Criticals");
        if (mod != null && mod.isEnabled()) {
            ((Criticals) mod).doCritical();
        }
    }
}
