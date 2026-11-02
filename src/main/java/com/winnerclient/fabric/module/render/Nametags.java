package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.mixin.GameRendererAccessor;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Standalone WinnerClient nametags implementation, modelled after Meteor's
 * 1.21.11 Nametags module but using only WinnerClient and Minecraft APIs.
 *
 * It deliberately renders in WinnerClient's existing HUD callback. Entity
 * positions are interpolated using the callback's tick delta, while the
 * vanilla label is cancelled by EntityRendererMixin to prevent duplicates.
 */
public final class Nametags extends Module {
    // Entity selection. EntityTypes is an optional comma-separated registry-ID allow-list.
    private final Setting.BoolSetting self = addBool("Self", "Show your own nametag in third person", false);
    private final Setting.BoolSetting useDisplayName = addBool("UseDisplayName", "Use the player's server display name", false);
    private final Setting.BoolSetting players = addBool("Players", "Render player nametags", true);
    private final Setting.BoolSetting mobs = addBool("Mobs", "Render living non-player nametags", false);
    private final Setting.BoolSetting items = addBool("Items", "Render dropped-item nametags", false);
    private final Setting.BoolSetting itemFrames = addBool("ItemFrames", "Render item-frame nametags", false);
    private final Setting.BoolSetting vehicles = addBool("Vehicles", "Render non-TNT vehicles", false);
    private final Setting.BoolSetting tnt = addBool("TNT", "Render primed TNT nametags", false);
    private final Setting.BoolSetting tntMinecarts = addBool("TNTMinecarts", "Render primed TNT minecart nametags", false);
    private final Setting.StringSetting entityTypes = addString("EntityTypes", "Optional comma-separated entity IDs; blank uses category toggles", "");
    private final Setting.BoolSetting ignoreBots = addBool("IgnoreBots", "Ignore players missing from the client player list", true);
    private final Setting.BoolSetting ignoreFriends = addBool("IgnoreFriends", "Do not render players in FriendNames", false);
    private final Setting.StringSetting friendNames = addString("FriendNames", "Comma-separated friend names or UUIDs", "");
    private final Setting.ColorSetting friendColor = addColor("FriendColor", "Color used for friend names", 0xFF55FFFF, true);
    private final Setting.BoolSetting useNameProtect = addBool("NameProtect", "Apply the existing WinnerClient NameHider to replacement labels", true);

    // General and player information.
    private final Setting.BoolSetting health = addBool("Health", "Show living entity health", true);
    private final Setting.BoolSetting ping = addBool("Ping", "Show player latency", true);
    private final Setting.BoolSetting distance = addBool("Distance", "Show distance in blocks", false);
    private final Setting.BoolSetting gamemode = addBool("Gamemode", "Show player game mode", false);
    private final Setting.BoolSetting equipment = addBool("Equipment", "Show player armor and hand items", true);
    private final Setting.BoolSetting stackCount = addBool("StackCount", "Show dropped-item stack counts", true);
    private final Setting.BoolSetting ignoreEmpty = addBool("IgnoreEmpty", "Do not reserve empty equipment slots", true);
    private final Setting.BoolSetting shadow = addBool("Shadow", "Draw text with a shadow", true);

    // Item details, including the Meteor-style enchantment and durability controls.
    private final Setting.BoolSetting displayEnchants = addBool("DisplayEnchants", "Show enchantment abbreviations on item displays", false);
    private final Setting.IntSetting enchantNameLength = addInt("EnchantNameLength", "Maximum enchantment name length", 3, 1, 12);
    private final Setting.NumberSetting enchantTextScale = addNum("EnchantTextScale", "Scale of enchantment text", 0.50, 0.25, 2.0, 0.05);
    private final Setting.ModeSetting enchantPosition = addMode("EnchantmentPosition", "Where enchantments are drawn", "Below", "Below", "Above");
    private final Setting.ModeSetting durability = addMode("Durability", "Durability display mode", "None", "None", "Percentage", "Total");

    // Render/culling controls.
    private final Setting.NumberSetting scale = addNum("Scale", "Base label scale", 1.0, 0.5, 4.0, 0.05);
    private final Setting.NumberSetting range = addNum("Range", "Maximum render distance", 64.0, 2.0, 256.0, 1.0);
    private final Setting.BoolSetting culling = addBool("Culling", "Limit the number of labels per frame", true);
    private final Setting.IntSetting maxTags = addInt("MaxTags", "Maximum labels rendered per frame", 80, 1, 500);
    private final Setting.BoolSetting throughWalls = addBool("ThroughWalls", "Render labels even when the entity is occluded", true);
    private final Setting.BoolSetting distanceScale = addBool("DistanceScale", "Make distant labels smaller", true);
    private final Setting.BoolSetting tntTimer = addBool("TNTTimer", "Show primed TNT fuse time", true);

    // ARGB colors, matching WinnerClient Setting.ColorSetting.
    private final Setting.ColorSetting nameColor = addColor("NameColor", "Name color", 0xFFFFFFFF, true);
    private final Setting.ColorSetting infoColor = addColor("InfoColor", "Secondary information color", 0xFFB8B8B8, true);
    private final Setting.ColorSetting healthColor = addColor("HealthColor", "Healthy entity color", 0xFF55FF55, true);
    private final Setting.ColorSetting lowHealthColor = addColor("LowHealthColor", "Low-health entity color", 0xFFFF5555, true);
    private final Setting.ColorSetting timerColor = addColor("TimerColor", "TNT timer color", 0xFFFF5555, true);
    private final Setting.ColorSetting enchantColor = addColor("EnchantColor", "Enchantment text color", 0xFFFFFFFF, true);
    private final Setting.ColorSetting durabilityColor = addColor("DurabilityColor", "Durability text color", 0xFFFFAA00, true);
    private final Setting.ColorSetting background = addColor("Background", "Label background", 0x99000000, true);

    private final List<Entity> visible = new ArrayList<>();
    // Vanilla EntityRenderer can query getDisplayName before the first client tick.
    // This flag prevents suppression from being based on an empty/stale candidate list.
    private boolean candidatesReady;

    public Nametags() {
        super("Nametags", "Customizable player, item, entity and TNT nametags", Category.RENDER);
    }

    public boolean showSelf() {
        return isEnabled() && self.getValue();
    }

    /**
     * Called by EntityRendererMixin. Suppression is deliberately narrower than
     * shouldRender: only an entity accepted by the latest completed onTick
     * candidate pass may be suppressed. This prevents vanilla labels vanishing
     * when range, culling, raycast visibility, or item contents reject a tag.
     */
    public static boolean suppressVanillaLabel(Entity entity) {
        Module module = ModuleManager.getModule("Nametags");
        if (!(module instanceof Nametags tags) || !tags.isEnabled()) return false;
        return tags.candidatesReady && tags.visible.contains(entity)
            && tags.isCurrentWorldEntity(entity) && !entity.isInvisible()
            && tags.hasRenderableContent(entity);
    }

    private boolean isCurrentWorldEntity(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && entity.getEntityWorld() == mc.world && !entity.isRemoved();
    }

    /** Empty dropped stacks and empty item frames produce no replacement tag. */
    private boolean hasRenderableContent(Entity entity) {
        if (entity instanceof ItemEntity item) return !item.getStack().isEmpty();
        if (entity instanceof ItemFrameEntity frame) return !frame.getHeldItemStack().isEmpty();
        return true;
    }

    /** Applies the existing NameHider only when this module's NameProtect setting allows it. */
    public static net.minecraft.text.Text applyNameProtect(net.minecraft.text.Text text) {
        Module module = ModuleManager.getModule("Nametags");
        if (module instanceof Nametags tags && tags.isEnabled() && !tags.useNameProtect.getValue()) return text;
        return com.winnerclient.fabric.module.player.NameHider.replace(text);
    }

    private boolean shouldRender(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String allowList = entityTypes.getValue().trim();
        boolean explicitAllowList = !allowList.isEmpty();

        if (entity instanceof PlayerEntity player) {
            if (player == mc.player && (!self.getValue() || mc.options.getPerspective().isFirstPerson())) return false;
            // A local player is valid even in a singleplayer world. Remote players
            // without a tab-list entry are the common client-side bot signature.
            if (ignoreBots.getValue() && player != mc.player && isBot(player)) return false;
            if (ignoreFriends.getValue() && isFriend(player)) return false;
            if (!explicitAllowList && !players.getValue()) return false;
        } else if (!explicitAllowList) {
            if (entity instanceof TntEntity) {
                if (!tnt.getValue() || ((TntEntity) entity).getFuse() < 0) return false;
            } else if (entity instanceof TntMinecartEntity) {
                if (!tntMinecarts.getValue() || ((TntMinecartEntity) entity).getFuseTicks() < 0) return false;
            } else if (entity instanceof ItemEntity) {
                if (!items.getValue()) return false;
            } else if (entity instanceof ItemFrameEntity) {
                if (!itemFrames.getValue()) return false;
            } else if (entity instanceof LivingEntity) {
                if (!mobs.getValue()) return false;
            } else if (entity instanceof VehicleEntity) {
                if (!vehicles.getValue()) return false;
            } else {
                return false;
            }
        }

        if (explicitAllowList) {
            Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
            if (id == null) return false;
            String full = id.toString().toLowerCase(Locale.ROOT);
            String path = id.getPath().toLowerCase(Locale.ROOT);
            for (String token : allowList.split(",")) {
                String wanted = token.trim().toLowerCase(Locale.ROOT);
                if (wanted.equals("*") || wanted.equals(full) || wanted.equals(path)) return true;
            }
            return false;
        }
        return true;
    }

    private boolean isBot(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;
        return mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null;
    }

    /** Standalone replacement for Meteor Friends: persisted FriendNames entries. */
    private boolean isFriend(PlayerEntity player) {
        String configured = friendNames.getValue();
        if (configured == null || configured.isBlank()) return false;
        String name = player.getName().getString();
        String uuid = player.getUuid().toString();
        for (String token : configured.split(",")) {
            String value = token.trim();
            if (value.equalsIgnoreCase(name) || value.equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        candidatesReady = false;
        visible.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        double maxDistanceSq = range.getValue() * range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldRender(entity) || entity.isInvisible()) continue;
            if (!hasRenderableContent(entity)) continue;
            if (entity.squaredDistanceTo(mc.player) > maxDistanceSq) continue;
            if (!throughWalls.getValue() && !canSee(entity)) continue;
            visible.add(entity);
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        visible.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(cameraPos)));
        if (culling.getValue() && visible.size() > maxTags.getValue()) {
            visible.subList(maxTags.getValue(), visible.size()).clear();
        }
        // Publish only after the complete filtering/culling pass is done.
        candidatesReady = true;
    }

    @Override
    protected void onDisable() {
        candidatesReady = false;
        visible.clear();
    }

    @Override
    public void onDisconnect() {
        candidatesReady = false;
        visible.clear();
    }

    private boolean canSee(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d from = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d to = entity.getEntityPos().add(0, entity.getHeight() * 0.75, 0);
        var hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL,
            RaycastContext.FluidHandling.NONE, entity));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    @Override
    public void onRender(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || visible.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float fov = ((GameRendererAccessor) mc.gameRenderer).invokeGetFov(camera, tickDelta, true);
        Projection projection = new Projection(cameraPos, camera.getYaw(), camera.getPitch(), fov, width, height);

        // Keep the nearest maxTags in onTick, but draw farthest first so nearer
        // labels win when projected rectangles overlap.
        for (int i = visible.size() - 1; i >= 0; i--) {
            Entity entity = visible.get(i);
            Vec3d labelPos = entity.getLerpedPos(tickDelta).add(0, labelHeight(entity), 0);
            ScreenPoint point = projection.project(labelPos);
            if (point == null) continue;
            drawTag(context, entity, point.x, point.y, point.depth);
        }
    }

    private double labelHeight(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof ItemFrameEntity) return 0.65;
        if (entity instanceof TntEntity || entity instanceof TntMinecartEntity) return entity.getHeight() + 0.45;
        return entity.getHeight() + 0.55;
    }

    private void drawTag(DrawContext context, Entity entity, float screenX, float screenY, double depth) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        List<Segment> title = new ArrayList<>();
        List<Segment> details = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();

        if (entity instanceof PlayerEntity player) {
            // NameHider is also applied by EntityRendererMixin; call it directly so
            // replacement labels have identical behavior.
            net.minecraft.text.Text nameText = useDisplayName.getValue() ? player.getDisplayName() : player.getName();
            String name = nameText.getString();
            if (useNameProtect.getValue()) {
                name = applyNameProtect(nameText).getString();
            }
            title.add(new Segment(name, isFriend(player) ? friendColor.getValue() : nameColor.getValue()));
            if (health.getValue()) {
                float hp = Math.max(0, player.getHealth() + player.getAbsorptionAmount());
                title.add(new Segment(" " + formatHealth(hp), healthArgb(player)));
            }
            if (ping.getValue()) {
                int latency = latency(player);
                if (latency >= 0) details.add(new Segment("[" + latency + "ms]", infoColor.getValue()));
            }
            if (gamemode.getValue()) {
                String mode = gameMode(player);
                if (!mode.isEmpty()) details.add(new Segment("[" + mode + "]", infoColor.getValue()));
            }
            if (distance.getValue()) details.add(new Segment(formatDistance(player), infoColor.getValue()));
            if (equipment.getValue()) stacks.addAll(equipment(player));
        } else if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getStack();
            if (stack.isEmpty()) return;
            title.add(new Segment(stack.getName().getString(), nameColor.getValue()));
            if (stackCount.getValue() && stack.getCount() > 1) title.add(new Segment(" x" + stack.getCount(), infoColor.getValue()));
            stacks.add(stack);
        } else if (entity instanceof ItemFrameEntity frame) {
            ItemStack stack = frame.getHeldItemStack();
            if (stack.isEmpty()) return;
            title.add(new Segment(stack.getName().getString(), nameColor.getValue()));
            if (stackCount.getValue() && stack.getCount() > 1) title.add(new Segment(" x" + stack.getCount(), infoColor.getValue()));
            stacks.add(stack);
        } else if (entity instanceof TntEntity primed) {
            title.add(new Segment("TNT", nameColor.getValue()));
            if (tntTimer.getValue()) title.add(new Segment(" " + fuseTime(primed.getFuse()), timerColor.getValue()));
        } else if (entity instanceof TntMinecartEntity primedMinecart) {
            title.add(new Segment("TNT Minecart", nameColor.getValue()));
            if (tntTimer.getValue()) title.add(new Segment(" " + fuseTime(primedMinecart.getFuseTicks()), timerColor.getValue()));
        } else if (entity instanceof LivingEntity living) {
            title.add(new Segment(living.getType().getName().getString(), nameColor.getValue()));
            if (health.getValue()) title.add(new Segment(" " + formatHealth(living.getHealth()), healthArgb(living)));
        } else {
            title.add(new Segment(entity.getType().getName().getString(), nameColor.getValue()));
        }

        if (title.isEmpty()) return;
        List<StackInfo> stackInfo = stackInfo(stacks, tr);
        float uiScale = (float) scale.getValue();
        if (distanceScale.getValue()) uiScale *= MathHelper.clamp((float) (8.0 / Math.max(8.0, depth)), 0.55f, 1.0f);

        int titleWidth = width(tr, title);
        int detailsWidth = details.isEmpty() ? 0 : width(tr, details);
        int textWidth = Math.max(titleWidth, detailsWidth);
        int rows = details.isEmpty() ? 1 : 2;
        int maxEnchantRows = 0;
        boolean anyDurability = false;
        int itemWidth = 0;
        for (StackInfo info : stackInfo) {
            maxEnchantRows = Math.max(maxEnchantRows, info.enchants().size());
            anyDurability |= info.durability() != null;
            itemWidth += Math.max(18, tr.getWidth(info.maxText()) + 2);
        }
        int itemRows = stackInfo.isEmpty() ? 0 : 1 + (anyDurability ? 1 : 0) + maxEnchantRows;
        int totalWidth = Math.max(textWidth + 6, itemWidth + 4);
        int totalHeight = rows * tr.fontHeight + (itemRows == 0 ? 0 : itemRows * tr.fontHeight + 2) + 4;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(screenX, screenY);
        matrices.scale(uiScale, uiScale);
        int left = -totalWidth / 2;
        int top = -totalHeight;
        context.fill(left, top, left + totalWidth, top + totalHeight, background.getValue());
        drawSegments(context, tr, title, left + 3, top + 2);
        if (!details.isEmpty()) drawSegments(context, tr, details, left + 3, top + 2 + tr.fontHeight);

        if (!stackInfo.isEmpty()) {
            int x = -itemWidth / 2;
            int itemY = top + rows * tr.fontHeight + 1;
            for (StackInfo info : stackInfo) {
                int columnWidth = Math.max(18, tr.getWidth(info.maxText()) + 2);
                int itemX = x + (columnWidth - 16) / 2;
                if (!info.stack().isEmpty()) {
                    context.drawItem(info.stack(), itemX, itemY);
                    context.drawStackOverlay(tr, info.stack(), itemX, itemY);
                }
                if (info.durability() != null) {
                    drawCentered(context, tr, info.durability(), x + columnWidth / 2, itemY + 16, durabilityColor.getValue());
                }
                int enchantY = itemY + 16 + (anyDurability ? tr.fontHeight : 0);
                if ("Above".equals(enchantPosition.getValue())) enchantY = itemY - info.enchants().size() * tr.fontHeight;
                for (String enchant : info.enchants()) {
                    drawCenteredScaled(context, tr, enchant, x + columnWidth / 2, enchantY, enchantColor.getValue(), (float) enchantTextScale.getValue());
                    if (!"Above".equals(enchantPosition.getValue())) enchantY += tr.fontHeight;
                    else enchantY += tr.fontHeight;
                }
                x += columnWidth;
            }
        }
        matrices.popMatrix();
    }

    private List<StackInfo> stackInfo(List<ItemStack> stacks, TextRenderer tr) {
        List<StackInfo> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() && ignoreEmpty.getValue()) continue;
            List<String> enchants = new ArrayList<>();
            if (displayEnchants.getValue() && !stack.isEmpty()) {
                ItemEnchantmentsComponent component = EnchantmentHelper.getEnchantments(stack);
                for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : component.getEnchantmentEntries()) {
                    String simple = Enchantment.getName(entry.getKey(), entry.getIntValue()).getString();
                    int colon = simple.indexOf(':');
                    if (colon >= 0) simple = simple.substring(colon + 1);
                    if (simple.length() > enchantNameLength.getValue()) simple = simple.substring(0, enchantNameLength.getValue());
                    enchants.add(simple);
                }
            }
            String dura = durabilityText(stack);
            String max = dura == null ? "" : dura;
            for (String enchant : enchants) if (tr.getWidth(enchant) > tr.getWidth(max)) max = enchant;
            result.add(new StackInfo(stack, enchants, dura, max));
        }
        return result;
    }

    private String durabilityText(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || "None".equals(durability.getValue())) return null;
        int remaining = stack.getMaxDamage() - stack.getDamage();
        return "Percentage".equals(durability.getValue())
            ? String.format(Locale.ROOT, "%.0f%%", remaining * 100.0 / stack.getMaxDamage())
            : Integer.toString(remaining);
    }

    private static void drawCentered(DrawContext context, TextRenderer tr, String text, int center, int y, int color) {
        context.drawText(tr, text, center - tr.getWidth(text) / 2, y, color, false);
    }

    private static void drawCenteredScaled(DrawContext context, TextRenderer tr, String text, int center, int y, int color, float scale) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(center, y);
        matrices.scale(scale, scale);
        context.drawText(tr, text, -tr.getWidth(text) / 2, 0, color, false);
        matrices.popMatrix();
    }

    private static int width(TextRenderer renderer, List<Segment> segments) {
        int width = 0;
        for (Segment segment : segments) width += renderer.getWidth(segment.text());
        return width;
    }

    private void drawSegments(DrawContext context, TextRenderer renderer, List<Segment> segments, int x, int y) {
        for (Segment segment : segments) {
            context.drawText(renderer, segment.text(), x, y, segment.color(), shadow.getValue());
            x += renderer.getWidth(segment.text());
        }
    }

    private static List<ItemStack> equipment(PlayerEntity player) {
        List<ItemStack> result = new ArrayList<>(6);
        // LivingEntity implements EquipmentHolder in the target mappings, so use the mapped equipment
        // accessor and explicit slots (helmet -> boots, then hands).
        result.add(player.getEquippedStack(EquipmentSlot.HEAD));
        result.add(player.getEquippedStack(EquipmentSlot.CHEST));
        result.add(player.getEquippedStack(EquipmentSlot.LEGS));
        result.add(player.getEquippedStack(EquipmentSlot.FEET));
        result.add(player.getEquippedStack(EquipmentSlot.MAINHAND));
        result.add(player.getEquippedStack(EquipmentSlot.OFFHAND));
        return result;
    }

    private static int latency(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    private static String gameMode(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return "";
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null || entry.getGameMode() == null) return "";
        GameMode mode = entry.getGameMode();
        return switch (mode) {
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            case SPECTATOR -> "Sp";
        };
    }

    private static String formatDistance(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return String.format(Locale.ROOT, "%.1fm", Math.sqrt(entity.squaredDistanceTo(mc.gameRenderer.getCamera().getCameraPos())));
    }

    private static String formatHealth(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fuseTime(int ticks) {
        return String.format(Locale.ROOT, "[%.1fs]", Math.max(0, ticks) / 20.0f);
    }

    private int healthArgb(LivingEntity entity) {
        float max = Math.max(1.0f, entity.getMaxHealth());
        return entity.getHealth() / max <= 0.333f ? lowHealthColor.getValue() : healthColor.getValue();
    }

    private record Segment(String text, int color) {}
    private record StackInfo(ItemStack stack, List<String> enchants, String durability, String maxText) {}
    private record ScreenPoint(float x, float y, double depth) {}

    private static final class Projection {
        private final Vec3d camera;
        private final Vec3d forward;
        private final Vec3d right;
        private final Vec3d up;
        private final double horizontalScale;
        private final double verticalScale;
        private final int width;
        private final int height;

        private Projection(Vec3d camera, float yaw, float pitch, float fov, int width, int height) {
            this.camera = camera;
            this.width = width;
            this.height = height;
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            this.forward = new Vec3d(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
            this.right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
            this.up = right.crossProduct(forward).normalize();
            double verticalFov = Math.tan(Math.toRadians(fov) / 2.0);
            this.verticalScale = Math.max(0.001, verticalFov);
            this.horizontalScale = Math.max(0.001, verticalFov * width / (double) height);
        }

        private ScreenPoint project(Vec3d world) {
            Vec3d relative = world.subtract(camera);
            double depth = relative.dotProduct(forward);
            if (depth <= 0.05) return null;
            double x = width / 2.0 + relative.dotProduct(right) / (depth * horizontalScale) * width / 2.0;
            double y = height / 2.0 - relative.dotProduct(up) / (depth * verticalScale) * height / 2.0;
            if (x < -200 || x > width + 200 || y < -200 || y > height + 200) return null;
            return new ScreenPoint((float) x, (float) y, depth);
        }
    }
}
