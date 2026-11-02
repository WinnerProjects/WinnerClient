package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.combat.Criticals;
import com.winnerclient.fabric.module.render.StorageESP;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Existing WinnerClient interaction hooks plus StorageESP opened-state tracking.
 *
 * <p>The original attackEntity/Criticals injection is intentionally unchanged.
 * The interactBlock hook runs at HEAD, records the clicked position (and the
 * other half of a double chest), then lets vanilla interaction continue.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        var mod = ModuleManager.getModule("Criticals");
        if (mod != null && mod.isEnabled()) {
            ((Criticals) mod).doCritical();
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var mod = ModuleManager.getModule("StorageESP");
        if (mod instanceof StorageESP storageESP) {
            storageESP.markInteracted(hitResult.getBlockPos());
        }
    }
}
