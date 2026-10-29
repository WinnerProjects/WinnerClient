package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.player.NameHider;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void replaceDisplayName(CallbackInfoReturnable<Text> cir) {
        Text display = cir.getReturnValue();
        if (display != null) {
            cir.setReturnValue(NameHider.replace(display));
        }
    }
}
