package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.CameraOverriddenEntity;
import com.winnerclient.fabric.module.player.Freecam;
import com.winnerclient.fabric.module.render.Freelook;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements CameraOverriddenEntity {
    @Unique private float cameraPitch;
    @Unique private float cameraYaw;
    @Unique private float anchorYaw;
    @Unique private boolean hasAnchor;
    @Unique private float savedYaw;
    @Unique private float savedPitch;

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onTurn(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Freelook.isActive()) {
            double pitchDelta = cursorDeltaY * 0.15;
            double yawDelta = cursorDeltaX * 0.15;

            if (!hasAnchor) {
                anchorYaw = cameraYaw;
                hasAnchor = true;
            }

            cameraPitch = MathHelper.clamp(cameraPitch + (float)pitchDelta, -90, 90);
            cameraYaw += (float)yawDelta;

            ci.cancel();
        } else if (Freecam.isActive()) {
            Freecam.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15);
            ci.cancel();
        } else if (hasAnchor) {
            hasAnchor = false;
        }
    }

    @Override
    public float freelook$getCameraPitch() { return cameraPitch; }

    @Override
    public float freelook$getCameraYaw() { return cameraYaw; }

    @Override
    public void freelook$setCameraPitch(float pitch) { this.cameraPitch = pitch; }

    @Override
    public void freelook$setCameraYaw(float yaw) { this.cameraYaw = yaw; }

    @Override
    public void freelook$setAnchorYaw(float yaw) { this.anchorYaw = yaw; this.hasAnchor = true; }

    @Override
    public boolean freelook$hasAnchor() { return hasAnchor; }

    @Override
    public float freelook$getSavedYaw() { return savedYaw; }

    @Override
    public float freelook$getSavedPitch() { return savedPitch; }

    @Override
    public void freelook$setSavedYaw(float yaw) { this.savedYaw = yaw; }

    @Override
    public void freelook$setSavedPitch(float pitch) { this.savedPitch = pitch; }
}
