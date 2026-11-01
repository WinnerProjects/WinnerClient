package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.player.Freecam;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldRenderer.class)
public abstract class FreecamWorldRendererMixin {

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;"
                + "updateCamera(Lnet/minecraft/client/render/Camera;"
                + "Lnet/minecraft/client/render/Frustum;Z)V"
        ),
        index = 2
    )
    private boolean winnerclient$freecamTerrain(boolean spectator) {
        return Freecam.isActive() || spectator;
    }
}
