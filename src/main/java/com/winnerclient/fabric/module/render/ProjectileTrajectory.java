package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectileTrajectory extends Module {

    private final Setting.ColorSetting lineColor = addColor(
        "LineColor",
        "Trajectory line color",
        0xFFFFFFFF,
        true
    );

    private final Setting.NumberSetting lineWidth = addNum(
        "LineWidth",
        "Trajectory line width",
        2.0,
        0.5,
        5.0,
        0.5
    );

    private final Setting.BoolSetting targetOutline = addBool(
        "TargetOutline",
        "Outline the block or entity at the predicted impact",
        true
    );

    private final Setting.ColorSetting targetColor = addColor(
        "TargetColor",
        "Impact outline color",
        0xFFFF5555,
        true
    );

    private final Setting.BoolSetting offhand = addBool(
        "Offhand",
        "Preview projectiles held in the offhand",
        true
    );

    private final Setting.IntSetting maxTicks = addInt(
        "MaxTicks",
        "Maximum simulated projectile ticks",
        200,
        20,
        200
    );

    public ProjectileTrajectory() {
        super(
            "ProjectileTrajectory",
            "Preview the trajectory of held projectiles",
            Category.RENDER
        );
    }

    private enum PhysicsOrder {
        POSITION_DRAG_GRAVITY,
        GRAVITY_DRAG_POSITION,
        GRAVITY_POSITION_DRAG
    }

    private record Projectile(
        Vec3d start,
        Vec3d velocity,
        double gravity,
        double drag,
        double waterDrag,
        boolean stopOnWater,
        PhysicsOrder order
    ) {}

    private record Impact(
        List<Vec3d> points,
        BlockPos block,
        Entity entity,
        boolean hit
    ) {}

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) return;

        WorldRenderer renderer = context.worldRenderer();
        if (renderer == null) return;

        ClientPlayerEntity player = mc.player;
        List<Projectile> projectiles = new ArrayList<>();

        // Prefer the main hand, then fall back to the offhand.
        addProjectiles(player, Hand.MAIN_HAND, projectiles);

        if (projectiles.isEmpty() && offhand.getValue()) {
            addProjectiles(player, Hand.OFF_HAND, projectiles);
        }

        if (projectiles.isEmpty()) return;

        try (var scope = renderer.startDrawingGizmos()) {
            for (Projectile projectile : projectiles) {
                Impact impact = simulate(mc.world, player, projectile);
                renderImpact(impact);
            }
        }
    }

    private void addProjectiles(
        ClientPlayerEntity player,
        Hand hand,
        List<Projectile> output
    ) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return;

        Vec3d start = player.getEyePos().add(0.0, -0.1, 0.0);
        Vec3d direction = player.getRotationVec(1.0f).normalize();

        if (stack.isOf(Items.BOW)) {
            if (!isUsingHand(player, hand)) return;

            float pull = BowItem.getPullProgress(player.getItemUseTime());
            if (pull < 0.1f) return;

            output.add(new Projectile(
                start,
                direction.multiply(3.0 * pull),
                0.05,
                0.99,
                0.6,
                false,
                PhysicsOrder.POSITION_DRAG_GRAVITY
            ));
            return;
        }

        if (stack.isOf(Items.CROSSBOW)) {
            ChargedProjectilesComponent charged =
                stack.get(DataComponentTypes.CHARGED_PROJECTILES);

            if (charged == null || charged.isEmpty()) return;

            boolean rocket = charged.contains(Items.FIREWORK_ROCKET);
            double speed = rocket ? 1.6 : 3.15;
            double gravity = rocket ? 0.0 : 0.05;
            double waterDrag = rocket ? 0.99 : 0.6;

            Vec3d velocity = direction.multiply(speed);

            output.add(new Projectile(
                start,
                velocity,
                gravity,
                0.99,
                waterDrag,
                false,
                PhysicsOrder.POSITION_DRAG_GRAVITY
            ));

            // A charged multishot crossbow normally contains 3 projectiles.
            if (charged.getProjectiles().size() >= 3) {
                float angle = (float) Math.toRadians(10.0);

                output.add(new Projectile(
                    start,
                    velocity.rotateY(angle),
                    gravity,
                    0.99,
                    waterDrag,
                    false,
                    PhysicsOrder.POSITION_DRAG_GRAVITY
                ));

                output.add(new Projectile(
                    start,
                    velocity.rotateY(-angle),
                    gravity,
                    0.99,
                    waterDrag,
                    false,
                    PhysicsOrder.POSITION_DRAG_GRAVITY
                ));
            }

            return;
        }

        if (stack.isOf(Items.TRIDENT)) {
            if (!isUsingHand(player, hand)) return;
            if (player.getItemUseTime() < TridentItem.MIN_DRAW_DURATION) return;

            // Riptide propels the player instead of throwing the trident.
            boolean riptide = stack.getEnchantments()
                .getEnchantments()
                .stream()
                .anyMatch(entry -> entry.matchesKey(Enchantments.RIPTIDE));

            if (riptide) return;

            output.add(new Projectile(
                start,
                direction.multiply(TridentItem.THROW_SPEED),
                0.05,
                0.99,
                0.99,
                false,
                PhysicsOrder.POSITION_DRAG_GRAVITY
            ));
            return;
        }

        if (stack.isOf(Items.SNOWBALL)
                || stack.isOf(Items.ENDER_PEARL)
                || stack.isOf(Items.EGG)
                || stack.isOf(Items.BLUE_EGG)
                || stack.isOf(Items.BROWN_EGG)) {

            output.add(new Projectile(
                start,
                direction.multiply(1.5),
                0.03,
                0.99,
                0.8,
                false,
                PhysicsOrder.GRAVITY_DRAG_POSITION
            ));
            return;
        }

        if (stack.isOf(Items.EXPERIENCE_BOTTLE)
                || stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION)) {

            boolean experience = stack.isOf(Items.EXPERIENCE_BOTTLE);

            output.add(new Projectile(
                start,
                angledDirection(player.getPitch(), player.getYaw(), -20.0f)
                    .multiply(experience ? 0.7 : 0.5),
                experience ? 0.07 : 0.05,
                0.99,
                0.8,
                false,
                PhysicsOrder.GRAVITY_DRAG_POSITION
            ));
            return;
        }

        if (stack.isOf(Items.FISHING_ROD)) {
            // Don't show a cast preview while the bobber is already out.
            if (player.fishHook != null) return;

            float pitch = player.getPitch();
            float yaw = player.getYaw();

            float h = MathHelper.cos(
                -yaw * MathHelper.RADIANS_PER_DEGREE - (float) Math.PI
            );
            float i = MathHelper.sin(
                -yaw * MathHelper.RADIANS_PER_DEGREE - (float) Math.PI
            );
            float j = -MathHelper.cos(
                -pitch * MathHelper.RADIANS_PER_DEGREE
            );
            float k = MathHelper.sin(
                -pitch * MathHelper.RADIANS_PER_DEGREE
            );

            double vertical = Math.abs(j) < 0.000001
                ? (k >= 0 ? -5.0 : 5.0)
                : MathHelper.clamp(-(k / j), -5.0f, 5.0f);

            Vec3d rodStart = start.add(-i * 0.3, 0.1, -h * 0.3);
            Vec3d rodVelocity = new Vec3d(-i, vertical, -h);

            double length = rodVelocity.length();
            if (length < 0.000001) return;

            rodVelocity = rodVelocity.multiply(0.6 / length + 0.5);

            output.add(new Projectile(
                rodStart,
                rodVelocity,
                0.03,
                0.92,
                0.92,
                true,
                PhysicsOrder.GRAVITY_POSITION_DRAG
            ));
        }
    }

    private static boolean isUsingHand(
        ClientPlayerEntity player,
        Hand hand
    ) {
        return player.isUsingItem() && player.getActiveHand() == hand;
    }

    private static Vec3d angledDirection(
        float pitch,
        float yaw,
        float pitchOffset
    ) {
        float radians = MathHelper.RADIANS_PER_DEGREE;

        double x = -MathHelper.sin(yaw * radians)
            * MathHelper.cos(pitch * radians);

        double y = -MathHelper.sin((pitch + pitchOffset) * radians);

        double z = MathHelper.cos(yaw * radians)
            * MathHelper.cos(pitch * radians);

        return new Vec3d(x, y, z).normalize();
    }

    private Impact simulate(
        ClientWorld world,
        ClientPlayerEntity player,
        Projectile projectile
    ) {
        List<Vec3d> points = new ArrayList<>();

        Vec3d position = projectile.start();
        Vec3d playerVelocity = player.getVelocity();

        Vec3d velocity = projectile.velocity().add(
            playerVelocity.x,
            player.isOnGround() ? 0.0 : playerVelocity.y,
            playerVelocity.z
        );

        points.add(position);

        for (int tick = 0; tick < maxTicks.getValue(); tick++) {
            BlockPos currentBlock = BlockPos.ofFloored(position);

            // Don't predict through chunks the client hasn't received.
            if (!world.getChunkManager().isChunkLoaded(
                    currentBlock.getX() >> 4,
                    currentBlock.getZ() >> 4)) {
                break;
            }

            boolean inWater = world.getFluidState(currentBlock)
                .isIn(FluidTags.WATER);

            double drag = inWater
                ? projectile.waterDrag()
                : projectile.drag();

            Vec3d next;

            switch (projectile.order()) {
                case POSITION_DRAG_GRAVITY -> {
                    next = position.add(velocity);
                    velocity = velocity.multiply(drag)
                        .add(0.0, -projectile.gravity(), 0.0);
                }

                case GRAVITY_DRAG_POSITION -> {
                    velocity = velocity
                        .add(0.0, -projectile.gravity(), 0.0)
                        .multiply(drag);
                    next = position.add(velocity);
                }

                case GRAVITY_POSITION_DRAG -> {
                    velocity = velocity.add(
                        0.0,
                        -projectile.gravity(),
                        0.0
                    );
                    next = position.add(velocity);
                    velocity = velocity.multiply(drag);
                }

                default -> throw new IllegalStateException(
                    "Unknown projectile physics order"
                );
            }

            RaycastContext.FluidHandling fluidHandling =
                projectile.stopOnWater()
                    ? RaycastContext.FluidHandling.WATER
                    : RaycastContext.FluidHandling.NONE;

            BlockHitResult blockHit = world.raycast(new RaycastContext(
                position,
                next,
                RaycastContext.ShapeType.COLLIDER,
                fluidHandling,
                player
            ));

            Vec3d closestHit = null;
            BlockPos hitBlock = null;
            Entity hitEntity = null;
            double closestDistance = Double.POSITIVE_INFINITY;

            if (blockHit.getType() != HitResult.Type.MISS) {
                closestHit = blockHit.getPos();
                hitBlock = blockHit.getBlockPos();
                closestDistance = position.squaredDistanceTo(closestHit);
            }

            Box searchBox = new Box(position, next).expand(1.0);

            for (Entity entity : world.getOtherEntities(
                    player,
                    searchBox,
                    candidate ->
                        candidate.isAlive()
                        && !candidate.isSpectator()
                        && !(candidate instanceof ProjectileEntity)
                        && !(candidate instanceof ItemEntity)
                        && !(candidate instanceof ExperienceOrbEntity)
                )) {

                Box hitBox = entity.getBoundingBox()
                    .expand(entity.getTargetingMargin());

                Optional<Vec3d> intersection = hitBox.raycast(position, next);
                if (intersection.isEmpty()) continue;

                Vec3d entityHit = intersection.get();
                double distance = position.squaredDistanceTo(entityHit);

                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestHit = entityHit;
                    hitEntity = entity;
                    hitBlock = null;
                }
            }

            if (closestHit != null) {
                points.add(closestHit);
                return new Impact(points, hitBlock, hitEntity, true);
            }

            position = next;
            points.add(position);

            if (position.y < world.getBottomY() - 20.0) break;
        }

        return new Impact(points, null, null, false);
    }

    private void renderImpact(Impact impact) {
        int color = lineColor.getValue();
        float width = lineWidth.getValue().floatValue();
        List<Vec3d> points = impact.points();

        for (int i = 0; i + 1 < points.size(); i++) {
            GizmoDrawing.line(
                points.get(i),
                points.get(i + 1),
                color,
                width
            );
        }

        if (!impact.hit() || points.isEmpty()) return;

        Vec3d end = points.get(points.size() - 1);
        double r = 0.1;

        // Small cross at the exact predicted impact point.
        GizmoDrawing.line(
            end.add(-r, 0, 0),
            end.add(r, 0, 0),
            color,
            width
        );

        GizmoDrawing.line(
            end.add(0, -r, 0),
            end.add(0, r, 0),
            color,
            width
        );

        GizmoDrawing.line(
            end.add(0, 0, -r),
            end.add(0, 0, r),
            color,
            width
        );

        if (!targetOutline.getValue()) return;

        DrawStyle outline = DrawStyle.stroked(
            targetColor.getValue(),
            width
        );

        if (impact.entity() != null) {
            GizmoDrawing.box(
                impact.entity().getBoundingBox()
                    .expand(impact.entity().getTargetingMargin()),
                outline
            );
        } else if (impact.block() != null) {
            GizmoDrawing.box(new Box(impact.block()), outline);
        }
    }
}
