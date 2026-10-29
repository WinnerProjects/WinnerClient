package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class SpoofDebug extends Module {
    public SpoofDebug() {
        super("SpoofDebug", "Replaces the Y coordinate sent to the server with a fake value; used to collect underground data.", Category.MISC);
    }

    private final Setting.NumberSetting spoofY = addNum("SpoofY", "The Y value to send to the server", -30.0, -256.0, 1024.0, 1.0);

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.age % 2 != 0) return;

        double y = spoofY.getValue();
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        boolean onGround = mc.player.isOnGround();

        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.Full(pos.x, y, pos.z, yaw, pitch, onGround, false));
    }
}
