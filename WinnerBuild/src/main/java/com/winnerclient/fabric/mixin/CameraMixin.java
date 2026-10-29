package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.CameraOverriddenEntity;
import com.winnerclient.fabric.module.player.Freecam;
import com.winnerclient.fabric.module.render.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1500)
public class CameraMixin {

    @Shadow private boolean thirdPerson;
    @Shadow private void setRotation(float yaw, float pitch) {}
    @Shadow private void setPos(double x, double y, double z) {}
    @Shadow private float clipToSpace(float distance) { return distance; }

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(World area, Entity focusedEntity, boolean tp, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (Freecam.isActive()) {
            this.thirdPerson = true;
            setRotation(Freecam.getYaw(tickDelta), Freecam.getPitch(tickDelta));
            setPos(Freecam.getX(tickDelta), Freecam.getY(tickDelta) + focusedEntity.getStandingEyeHeight(), Freecam.getZ(tickDelta));
        } else if (Freelook.isActive() && focusedEntity instanceof CameraOverriddenEntity coe) {
            float flYaw = coe.freelook$getCameraYaw();
            float flPitch = coe.freelook$getCameraPitch();

            setRotation(flYaw, flPitch);

            Vec3d entityPos = focusedEntity.getEntityPos();
            double eyeY = entityPos.y + focusedEntity.getStandingEyeHeight();

            double dist = Freelook.getCameraDistance();
            float pitchRad = flPitch * 0.017453292F;
            float yawRad = flYaw * 0.017453292F;

            double dx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
            double dy = -MathHelper.sin(pitchRad);
            double dz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);

            dist = clipToSpace((float)dist);

            setPos(
                entityPos.x + dx * dist,
                eyeY + dy * dist,
                entityPos.z + dz * dist
            );
        }
    }
}
