package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.combat.TriggerBot;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void winnerclient$queueTriggerBotInput(CallbackInfo ci) {
        Module module = ModuleManager.getModule("TriggerBot");

        if (module instanceof TriggerBot triggerBot && triggerBot.isEnabled()) {
            triggerBot.queueAttackInput();
        }
    }
}
