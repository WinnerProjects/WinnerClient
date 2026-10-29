package com.winnerclient.fabric;

public interface CameraOverriddenEntity {
    float freelook$getCameraPitch();
    float freelook$getCameraYaw();
    void freelook$setCameraPitch(float pitch);
    void freelook$setCameraYaw(float yaw);
    void freelook$setAnchorYaw(float yaw);
    boolean freelook$hasAnchor();
    float freelook$getSavedYaw();
    float freelook$getSavedPitch();
    void freelook$setSavedYaw(float yaw);
    void freelook$setSavedPitch(float pitch);
}
