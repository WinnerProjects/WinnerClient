package com.winnerclient.fabric.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class FullbrightLightmapMixin {
    @Shadow
    @Final
    private GpuTexture glTexture;

    @Inject(method = "update", at = @At("RETURN"))
    private void winnerclient$fullbright(float tickProgress, CallbackInfo ci) {
        Module fullbright = ModuleManager.getModule("Fullbright");

        if (fullbright == null || !fullbright.isEnabled()) return;

        RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorTexture(glTexture, 0xFFFFFFFF);
    }
}
