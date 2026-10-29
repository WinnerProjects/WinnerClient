package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class NoFall extends Module {
    private final Setting.ModeSetting mode = addMode("Mode", "NoFall mode", "NoGroundSpoof", "NoGroundSpoof", "OnGround", "Vanilla");

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.fallDistance <= 2.0f) return;

        String m = mode.getValue();
        switch (m) {
            case "NoGroundSpoof":
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), false, false));
                mc.player.fallDistance = -0.5f;
                break;
            case "OnGround":
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), true, false));
                mc.player.fallDistance = 0;
                break;
            case "Vanilla":
                mc.player.fallDistance = 0;
                break;
        }
    }
}
