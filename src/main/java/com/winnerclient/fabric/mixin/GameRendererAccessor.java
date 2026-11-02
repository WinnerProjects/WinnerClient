package com.winnerclient.fabric.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Existing WinnerClient screen-ESP dependency: exposes vanilla's effective
 * FOV calculation without duplicating camera/FOV option logic.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float invokeGetFov(Camera camera, float tickProgress, boolean handFov);

    /** Vanilla's private active projection calculation for this render frame. */
    @Invoker("getProjectionMatrix")
    Matrix4f invokeGetProjectionMatrix(float tickProgress);
}
