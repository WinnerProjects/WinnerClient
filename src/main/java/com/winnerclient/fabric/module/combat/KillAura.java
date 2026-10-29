package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    private final Setting.NumberSetting range = addNum("Range", "Attack range", 4.0, 2.0, 6.0, 0.1);
    private final Setting.NumberSetting aps    = addNum("APS", "Attacks per second", 10.0, 1.0, 20.0, 0.5);
    private final Setting.BoolSetting players  = addBool("Players", "Target players", true);
    private final Setting.BoolSetting hostile  = addBool("Hostile", "Target hostile mobs", false);
    private final Setting.BoolSetting passive  = addBool("Passive", "Target passive mobs", false);
    private final Setting.BoolSetting autoBlock = addBool("AutoBlock", "Auto shield block", false);
    private final Setting.BoolSetting cooldownSync = addBool("CooldownSync", "Wait for full cooldown", false);
    private final Setting.ModeSetting rotMode  = addMode("Rotations", "Rotation mode", "Snap", "Snap", "Smooth", "None");
    private final Setting.ModeSetting sortMode = addMode("Sort", "Target sorting", "Distance", "Distance", "Angle", "Health");

    private int tickCounter;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        tickCounter++;
        int delay = Math.max(1, (int)(20.0 / aps.getValue()));
        if (tickCounter % delay != 0) return;

        float threshold = cooldownSync.getValue() ? 0.99f : 0.85f;
        if (mc.player.getAttackCooldownProgress(0) < threshold) return;

        Entity target = findTarget(mc);
        if (target == null) return;

        doRotations(mc, target);

        if (autoBlock.getValue() && mc.player.getOffHandStack().getItem() instanceof net.minecraft.item.ShieldItem) {
            mc.options.useKey.setPressed(true);
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.options.useKey.setPressed(false);
        }
    }

    private Entity findTarget(MinecraftClient mc) {
        Box searchBox = mc.player.getBoundingBox().expand(range.getValue());
        List<Entity> entities = mc.world.getEntitiesByClass(Entity.class, searchBox, e -> e != mc.player && e instanceof LivingEntity && ((LivingEntity) e).isAlive());

        entities.removeIf(e -> {
            if (e instanceof PlayerEntity p) {
                if (p.isCreative() || p.isSpectator()) return true;
                if (mc.player.isTeammate(p)) return true;
                return !players.getValue();
            }
            if (isHostile(e)) return !hostile.getValue();
            if (isPassive(e)) return !passive.getValue();
            return true;
        });

        if (entities.isEmpty()) return null;

        String sort = sortMode.getValue();
        switch (sort) {
            case "Distance":
                entities.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
                break;
            case "Angle":
                entities.sort(Comparator.comparingDouble(e -> getAngleDist(mc, e)));
                break;
            case "Health":
                entities.sort(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()));
                break;
        }
        return entities.get(0);
    }

    private double getAngleDist(MinecraftClient mc, Entity e) {
        Vec3d diff = e.getEntityPos().subtract(mc.player.getEyePos());
        double len = diff.length();
        if (len < 1e-8) return 0;
        double pitch = Math.asin(Math.max(-1, Math.min(1, -diff.y / len))) * 180.0 / Math.PI;
        double yaw = Math.atan2(-diff.x, diff.z) * 180.0 / Math.PI;
        return Math.abs(yaw - mc.player.getYaw()) + Math.abs(pitch - mc.player.getPitch());
    }

    private void doRotations(MinecraftClient mc, Entity target) {
        String mode = rotMode.getValue();
        if (mode.equals("None")) return;
        Vec3d eyes = target.getEyePos();
        Vec3d diff = eyes.subtract(mc.player.getEyePos());
        double len = diff.length();
        if (len < 1e-8) return;
        double pitch = Math.asin(Math.max(-1, Math.min(1, -diff.y / len))) * 180.0 / Math.PI;
        double yaw = Math.atan2(-diff.x, diff.z) * 180.0 / Math.PI;

        if (mode.equals("Snap")) {
            mc.player.setYaw((float) yaw);
            mc.player.setPitch((float) pitch);
        } else if (mode.equals("Smooth")) {
            float currYaw = mc.player.getYaw();
            float currPitch = mc.player.getPitch();
            mc.player.setYaw(currYaw + (float) ((yaw - currYaw) * 0.3));
            mc.player.setPitch(currPitch + (float) ((pitch - currPitch) * 0.3));
        }
    }

    private boolean isHostile(Entity e) {
        return e.getType().getSpawnGroup().getName().equals("monster");
    }

    private boolean isPassive(Entity e) {
        String g = e.getType().getSpawnGroup().getName();
        return g.equals("creature") || g.equals("ambient") || g.equals("water_creature");
    }
}