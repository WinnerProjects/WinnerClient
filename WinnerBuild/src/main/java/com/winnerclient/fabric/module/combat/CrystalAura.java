package com.winnerclient.fabric.module.combat;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.List;

public class CrystalAura extends Module {
    private final Setting.NumberSetting cps = addNum("CPS", "Crystals per second", 10.0, 1.0, 30.0, 1.0);
    private final Setting.BoolSetting placeThroughWalls = addBool("PlaceThroughWalls", "Place crystals through walls", false);
    private final Setting.NumberSetting minDamage = addNum("MinDamage", "Minimum damage to target", 6.0, 1.0, 36.0, 1.0);
    private final Setting.NumberSetting maxSelfDamage = addNum("MaxSelfDamage", "Maximum self damage", 4.0, 1.0, 20.0, 1.0);
    private final Setting.NumberSetting range = addNum("Range", "Place/break range", 6.0, 3.0, 8.0, 0.5);
    private final Setting.NumberSetting targetRange = addNum("TargetRange", "Max distance to target", 12.0, 4.0, 20.0, 1.0);
    private final Setting.BoolSetting autoObsidian = addBool("AutoObsidian", "Auto place obsidian on air blocks", true);
    private final Setting.ModeSetting placeMode = addMode("PlaceMode", "Crystal placement targeting", "BestDamage", "Nearest", "BestDamage");
    private final Setting.BoolSetting silentRotate = addBool("SilentRotate", "Silent rotation (packet only)", true);

    private long lastActionTime;
    private BlockPos pendingObsidian;

    public CrystalAura() {
        super("CrystalAura", "Auto place and detonate End Crystals", Category.COMBAT);
    }

    private int crystalSlotCache = -1;
    private int prevSlot = -1;

    @Override
    public void onDisable() {
        pendingObsidian = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        long delay = (long) (1000.0 / cps.getValue());
        if (now - lastActionTime < delay) return;

        LivingEntity target = findBestTarget(mc);
        if (target == null) return;

        if (detonateCrystal(mc, target)) {
            lastActionTime = now;
            return;
        }

        if (placeCrystal(mc, target)) {
            detonateNearby(mc, target);
            lastActionTime = now;
        }
    }

    private LivingEntity findBestTarget(MinecraftClient mc) {
        double tr = targetRange.getValue();
        List<Entity> entities = mc.world.getEntitiesByClass(Entity.class,
                mc.player.getBoundingBox().expand(tr),
                e -> e != mc.player && e instanceof LivingEntity le && le.isAlive());

        entities.removeIf(e -> {
            if (e instanceof PlayerEntity p) {
                if (p.isCreative() || p.isSpectator()) return true;
                return mc.player.isTeammate(p);
            }
            return false;
        });

        if (entities.isEmpty()) return null;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : entities) {
            double d = mc.player.squaredDistanceTo(e);
            if (best == null || d < bestScore) {
                bestScore = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private boolean detonateCrystal(MinecraftClient mc, LivingEntity target) {
        double r = range.getValue();
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(r),
                e -> e.isAlive());

        EndCrystalEntity best = null;
        double bestScore = -Double.MAX_VALUE;

        for (EndCrystalEntity crystal : crystals) {
            Vec3d crystalPos = crystal.getEntityPos();
            double targetDamage = calculateDamage(crystalPos, target);
            double selfDamage = calculateDamage(crystalPos, mc.player);

            if (targetDamage < minDamage.getValue()) continue;
            if (selfDamage > maxSelfDamage.getValue()) continue;

            double score = targetDamage - selfDamage * 1.5;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }

        if (best == null) return false;
        lookAt(best.getEntityPos());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private record CrystalPlace(BlockPos pos, BlockPos above, double targetDmg, double selfDmg, double playerDist) {}

    private boolean placeCrystal(MinecraftClient mc, LivingEntity target) {
        int crystalSlot = findCrystalSlot(mc.player);
        if (crystalSlot == -1) return false;

        double tr = targetRange.getValue();

        if (pendingObsidian != null) {
            if (!mc.world.getBlockState(pendingObsidian).isAir()) {
                BlockPos placed = pendingObsidian;
                pendingObsidian = null;
                Vec3d center = crystalPos(placed);
                if (target.squaredDistanceTo(center) > tr * tr) return false;
                double targetDmg = calculateDamage(center, target);
                double selfDmg = calculateDamage(center, mc.player);
                if (targetDmg < minDamage.getValue() || selfDmg > maxSelfDamage.getValue()) return false;
                return placeCrystalOn(mc, crystalSlot, placed);
            }
            tryPlaceObsidian(mc, pendingObsidian);
            return false;
        }

        double r = range.getValue();
        Vec3d playerPos = mc.player.getEntityPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        CrystalPlace best = null;
        CrystalPlace obsidianTarget = null;
        String mode = placeMode.getValue();
        int rad = (int) Math.ceil(r);

        int px = MathHelper.floor(playerPos.x);
        int py = MathHelper.floor(playerPos.y);
        int pz = MathHelper.floor(playerPos.z);
        int tx = MathHelper.floor(target.getX());
        int ty = MathHelper.floor(target.getY());
        int tz = MathHelper.floor(target.getZ());

        int minX = Math.min(px, tx) - rad;
        int maxX = Math.max(px, tx) + rad;
        int minZ = Math.min(pz, tz) - rad;
        int maxZ = Math.max(pz, tz) + rad;
        int minY = Math.min(py, ty) - 2;
        int maxY = Math.max(py, ty) + 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    BlockPos pos = mutable.toImmutable();
                    BlockPos above = pos.up();

                    if (!mc.world.getBlockState(above).isAir()) continue;

                    double playerDist2 = mc.player.squaredDistanceTo(Vec3d.ofCenter(above));
                    if (playerDist2 > r * r) continue;
                    double targetDist2 = target.squaredDistanceTo(Vec3d.ofCenter(above));
                    if (targetDist2 > tr * tr) continue;

                    boolean hasCrystal = !mc.world.getEntitiesByClass(EndCrystalEntity.class,
                            new Box(above).expand(0.1),
                            Entity::isAlive).isEmpty();
                    if (hasCrystal) continue;

                    if (!placeThroughWalls.getValue() && !hasLineOfSight(mc, above)) continue;

                    Vec3d crystalCenter = crystalPos(pos);
                    double targetDmg = calculateDamage(crystalCenter, target);
                    double selfDmg = calculateDamage(crystalCenter, mc.player);

                    var state = mc.world.getBlockState(pos);
                    boolean isValidBlock = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);

                    if (isValidBlock) {
                        if (targetDmg < minDamage.getValue()) continue;
                        if (selfDmg > maxSelfDamage.getValue()) continue;

                        double score = targetDmg - selfDmg * 1.5;
                        if (best == null) {
                            best = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                        } else if (mode.equals("Nearest")) {
                            if (playerDist2 < best.playerDist) {
                                best = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                            }
                        } else {
                            double curScore = best.targetDmg - best.selfDmg * 1.5;
                            if (score > curScore) {
                                best = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                            }
                        }
                    } else if (autoObsidian.getValue() && state.isAir() && canPlaceAgainst(pos)) {
                        if (targetDmg < minDamage.getValue()) continue;
                        if (selfDmg > maxSelfDamage.getValue()) continue;

                        double score = targetDmg - selfDmg * 1.5;
                        if (obsidianTarget == null) {
                            obsidianTarget = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                        } else if (mode.equals("Nearest")) {
                            if (playerDist2 < obsidianTarget.playerDist) {
                                obsidianTarget = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                            }
                        } else {
                            double curScore = obsidianTarget.targetDmg - obsidianTarget.selfDmg * 1.5;
                            if (score > curScore) {
                                obsidianTarget = new CrystalPlace(pos, above, targetDmg, selfDmg, playerDist2);
                            }
                        }
                    }
                }
            }
        }

        if (best != null) {
            return placeCrystalOn(mc, crystalSlot, best.pos);
        }

        if (obsidianTarget != null) {
            pendingObsidian = obsidianTarget.pos;
            tryPlaceObsidian(mc, obsidianTarget.pos);
        }
        return false;
    }

    private boolean placeCrystalOn(MinecraftClient mc, int crystalSlot, BlockPos pos) {
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;
        switchToSlot(mc, crystalSlot);
        lookAt(Vec3d.ofCenter(pos));
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        switchBackSlot(mc);
        return true;
    }

    private Vec3d crystalPos(BlockPos support) {
        return new Vec3d(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
    }

    private boolean canPlaceAgainst(BlockPos pos) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        for (Direction dir : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(dir)).isAir()) return true;
        }
        return false;
    }

    private boolean tryPlaceObsidian(MinecraftClient mc, BlockPos pos) {
        int slot = findObsidianSlot(mc.player);
        if (slot == -1) return false;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isAir()) {
                useSlotSilent(mc, slot);
                lookAt(Vec3d.ofCenter(neighbor));
                BlockHitResult hit = new BlockHitResult(
                        Vec3d.ofCenter(neighbor), dir.getOpposite(), neighbor, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private double calculateDamage(Vec3d explosionPos, Entity entity) {
        if (entity == null) return 0;
        float power = 6.0f;
        float f = power * 2.0f;
        double distance = entity.getEntityPos().distanceTo(explosionPos);
        if (distance > f) return 0;
        double exposure = getExposure(explosionPos, entity);
        return Math.floor((1.0 - distance / f) * (f * exposure + 1.0));
    }

    private double getExposure(Vec3d explosionPos, Entity entity) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return 1.0;
        Box bb = entity.getBoundingBox();
        Vec3d[] corners = {
                new Vec3d(bb.minX, bb.minY, bb.minZ),
                new Vec3d(bb.minX, bb.minY, bb.maxZ),
                new Vec3d(bb.minX, bb.maxY, bb.minZ),
                new Vec3d(bb.minX, bb.maxY, bb.maxZ),
                new Vec3d(bb.maxX, bb.minY, bb.minZ),
                new Vec3d(bb.maxX, bb.minY, bb.maxZ),
                new Vec3d(bb.maxX, bb.maxY, bb.minZ),
                new Vec3d(bb.maxX, bb.maxY, bb.maxZ)
        };
        int hits = 0;
        for (Vec3d corner : corners) {
            var result = mc.world.raycast(new RaycastContext(
                    explosionPos, corner,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity));
            if (result.getType() == net.minecraft.util.hit.HitResult.Type.MISS) hits++;
        }
        return hits / 8.0;
    }

    private boolean hasLineOfSight(MinecraftClient mc, BlockPos pos) {
        var result = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        return result.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private void detonateNearby(MinecraftClient mc, LivingEntity target) {
        double r = range.getValue();
        EndCrystalEntity best = null;
        double bestScore = -Double.MAX_VALUE;

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(r), e -> e.isAlive())) {
            Vec3d pos = crystal.getEntityPos();
            double targetDmg = calculateDamage(pos, target);
            double selfDmg = calculateDamage(pos, mc.player);
            if (targetDmg < minDamage.getValue()) continue;
            if (selfDmg > maxSelfDamage.getValue()) continue;

            double score = targetDmg - selfDmg * 1.5;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }

        if (best == null) return;
        lookAt(best.getEntityPos());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void switchToSlot(MinecraftClient mc, int slot) {
        if (slot < 0 || slot > 8) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (player.getInventory().selectedSlot == slot) {
            prevSlot = -1;
            return;
        }
        prevSlot = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = slot;
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
    }

    private void switchBackSlot(MinecraftClient mc) {
        if (prevSlot < 0 || prevSlot > 8) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        player.getInventory().selectedSlot = prevSlot;
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(prevSlot));
        prevSlot = -1;
    }

    private void useSlotSilent(MinecraftClient mc, int slot) {
        if (slot < 0 || slot > 8) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (player.getInventory().selectedSlot == slot) return;
        int prev = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = slot;
        player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        player.getInventory().selectedSlot = prev;
    }

    private void lookAt(Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double len = diff.length();
        if (len < 1e-8) return;
        float yaw = (float) (Math.atan2(-diff.x, diff.z) * 180.0 / Math.PI);
        float pitch = (float) (-Math.asin(Math.max(-1, Math.min(1, diff.y / len))) * 180.0 / Math.PI);
        if (silentRotate.getValue()) {
            mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), true));
        } else {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }

    private int findCrystalSlot(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() instanceof EndCrystalItem) return player.getInventory().selectedSlot;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof EndCrystalItem) return i;
        }
        return -1;
    }

    private int findObsidianSlot(ClientPlayerEntity player) {
        if (player.getMainHandStack().getItem() == Blocks.OBSIDIAN.asItem()) return player.getInventory().selectedSlot;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Blocks.OBSIDIAN.asItem()) return i;
        }
        return -1;
    }
}
