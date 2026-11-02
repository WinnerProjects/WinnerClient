package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.render.Nametags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents vanilla labels from being drawn underneath the replacement. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void winner$replaceDisplayName(Entity entity, CallbackInfoReturnable<Text> cir) {
        if (Nametags.suppressVanillaLabel(entity)) {
            cir.setReturnValue(null);
            return;
        }
        Text display = cir.getReturnValue();
        if (display != null) cir.setReturnValue(Nametags.applyNameProtect(display));
    }
}
