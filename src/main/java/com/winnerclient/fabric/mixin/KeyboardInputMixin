package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.player.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void winnerclient$freecamInput(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!Freecam.isActive()
            || mc.player == null
            || (Object) this != mc.player.input) {
            return;
        }

        // Save the keys for moving the camera.
        Freecam.captureInput(this.playerInput);

        // Prevent the same keys from moving the player.
        this.playerInput = new PlayerInput(
            false,
            false,
            false,
            false,
            false,
            Freecam.shouldKeepSneaking(),
            false
        );

        this.movementVector = Vec2f.ZERO;
    }
}
