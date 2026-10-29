package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

public class XRay extends Module {
    private final Setting.ModeSetting preset = addMode("Preset", "Block selection preset", "Default", "Default", "All Ores", "All", "Custom");
    private final Setting.BoolSetting diamond = addBool("Diamond", "Show diamond ore", true);
    private final Setting.BoolSetting iron = addBool("Iron", "Show iron ore", true);
    private final Setting.BoolSetting gold = addBool("Gold", "Show gold ore", true);
    private final Setting.BoolSetting lapis = addBool("Lapis", "Show lapis ore", true);
    private final Setting.BoolSetting redstone = addBool("Redstone", "Show redstone ore", true);
    private final Setting.BoolSetting emerald = addBool("Emerald", "Show emerald ore", true);
    private final Setting.BoolSetting copper = addBool("Copper", "Show copper ore", false);
    private final Setting.BoolSetting netherOres = addBool("NetherOres", "Show nether gold/quartz", true);
    private final Setting.BoolSetting ancientDebris = addBool("Debris", "Show ancient debris", true);
    private final Setting.BoolSetting chests = addBool("Chests", "Show chests", false);
    private final Setting.BoolSetting spawners = addBool("Spawners", "Show spawners", false);

    private final Set<Block> visibleBlocks = new HashSet<>();
    private double oldGamma;

    public XRay() {
        super("XRay", "See ores through walls", Category.RENDER);
        rebuildBlockList();
    }

    @Override
    public void onEnable() {
        rebuildBlockList();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            oldGamma = mc.options.getGamma().getValue();
            mc.options.getGamma().setValue(16.0);
        }
        if (mc.worldRenderer != null) {
            mc.worldRenderer.refreshTerrainSampler();
            mc.worldRenderer.scheduleTerrainUpdate();
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.getGamma().setValue(oldGamma);
        }
        if (mc.worldRenderer != null) {
            mc.worldRenderer.refreshTerrainSampler();
            mc.worldRenderer.scheduleTerrainUpdate();
        }
    }

    private void rebuildBlockList() {
        visibleBlocks.clear();
        String mode = preset.getValue();

        if (mode.equals("All")) {
            Registries.BLOCK.forEach(visibleBlocks::add);
            return;
        }

        boolean useCustom = mode.equals("Custom");
        if (useCustom || mode.equals("Default") || mode.equals("All Ores")) {
            addIf(useCustom, diamond, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
            addIf(useCustom, iron, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            addIf(useCustom, gold, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
            addIf(useCustom, lapis, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
            addIf(useCustom, redstone, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            addIf(useCustom, emerald, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        }
        if (useCustom || mode.equals("All Ores")) {
            addIf(useCustom, copper, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            addIf(useCustom, netherOres, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE);
            addIf(useCustom, ancientDebris, Blocks.ANCIENT_DEBRIS);
        }
        if (mode.equals("Default") || mode.equals("All Ores")) {
            visibleBlocks.add(Blocks.NETHER_GOLD_ORE);
            visibleBlocks.add(Blocks.NETHER_QUARTZ_ORE);
            visibleBlocks.add(Blocks.ANCIENT_DEBRIS);
        }
        if (useCustom) {
            addIf(useCustom, chests, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL);
            addIf(useCustom, spawners, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER);
        }
    }

    private void addIf(boolean useCustom, Setting.BoolSetting setting, Block... blocks) {
        if (!useCustom || setting.getValue()) {
            for (Block b : blocks) visibleBlocks.add(b);
        }
    }

    public static boolean isActive() {
        Module mod = ModuleManager.getModule("XRay");
        return mod != null && mod.isEnabled();
    }

    public static boolean isVisible(Block block) {
        if (!isActive()) return true;
        if (block == Blocks.AIR) return true;
        Module mod = ModuleManager.getModule("XRay");
        return ((XRay) mod).visibleBlocks.contains(block);
    }
}
