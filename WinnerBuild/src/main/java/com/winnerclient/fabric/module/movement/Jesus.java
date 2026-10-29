package com.winnerclient.fabric.module.movement;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

public class Jesus extends Module {
    private final Setting.ModeSetting mode = addMode("Mode", "Jesus mode", "Solid", "Solid", "Dolphin", "Matrix");

    public Jesus() {
        super("Jesus", "Walk on water/lava surfaces", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isTouchingWater() && !mc.player.isInLava()) return;

        String m = mode.getValue();
        switch (m) {
            case "Solid":
                mc.player.setVelocity(mc.player.getVelocity().x, 0.08, mc.player.getVelocity().z);
                break;
            case "Dolphin":
                if (mc.player.isSubmergedInWater()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.12, mc.player.getVelocity().z);
                }
                mc.player.input.playerInput = new PlayerInput(
                    mc.player.input.playerInput.forward(),
                    mc.player.input.playerInput.backward(),
                    mc.player.input.playerInput.left(),
                    mc.player.input.playerInput.right(),
                    true,
                    mc.player.input.playerInput.sneak(),
                    mc.player.input.playerInput.sprint()
                );
                break;
            case "Matrix":
                if (mc.player.isTouchingWater()) {
                    mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.005, mc.player.getZ());
                    mc.player.setVelocity(0, 0, 0);
                    mc.player.setOnGround(true);
                }
                break;
        }
    }
}