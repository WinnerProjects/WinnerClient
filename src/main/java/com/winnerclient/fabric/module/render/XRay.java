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
    private final Setting.ModeSetting preset = addMode(
            "Preset",
            "Block selection preset",
            "Default",
            "Default",
            "All Ores",
            "All",
            "Custom"
    );

    private final Setting.BoolSetting diamond =
            addBool("Diamond", "Show diamond ore", true);
    private final Setting.BoolSetting iron =
            addBool("Iron", "Show iron ore", true);
    private final Setting.BoolSetting gold =
            addBool("Gold", "Show gold ore", true);
    private final Setting.BoolSetting lapis =
            addBool("Lapis", "Show lapis ore", true);
    private final Setting.BoolSetting redstone =
            addBool("Redstone", "Show redstone ore", true);
    private final Setting.BoolSetting emerald =
            addBool("Emerald", "Show emerald ore", true);
    private final Setting.BoolSetting copper =
            addBool("Copper", "Show copper ore", false);
    private final Setting.BoolSetting netherOres =
            addBool("NetherOres", "Show nether gold/quartz", true);
    private final Setting.BoolSetting ancientDebris =
            addBool("Debris", "Show ancient debris", true);
    private final Setting.BoolSetting chests =
            addBool("Chests", "Show chests", false);
    private final Setting.BoolSetting spawners =
            addBool("Spawners", "Show spawners", false);

    // Chunk render iş parçacıkları değişmez bir liste okur.
    private volatile Set<Block> visibleBlocks = Set.of();
    private String lastSettings = "";
    private double oldGamma;

    public XRay() {
        super("XRay", "See ores through walls", Category.RENDER);
        rebuildBlockList();
        lastSettings = settingsSignature();
    }

    @Override
    public void onEnable() {
        Module reverse = ModuleManager.getModule("ReverseXRay");

        if (reverse != null && reverse.isEnabled()) {
            reverse.setEnabled(false);
        }

        rebuildBlockList();
        lastSettings = settingsSignature();

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options != null) {
            oldGamma = mc.options.getGamma().getValue();
            mc.options.getGamma().setValue(16.0);
        }

        reloadRenderer();
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options != null) {
            mc.options.getGamma().setValue(oldGamma);
        }

        reloadRenderer();
    }

    @Override
    public void onTick() {
        String current = settingsSignature();

        if (!current.equals(lastSettings)) {
            lastSettings = current;
            rebuildBlockList();
            reloadRenderer();
        }
    }

    private String settingsSignature() {
        return preset.getValue()
                + "|" + diamond.getValue()
                + "|" + iron.getValue()
                + "|" + gold.getValue()
                + "|" + lapis.getValue()
                + "|" + redstone.getValue()
                + "|" + emerald.getValue()
                + "|" + copper.getValue()
                + "|" + netherOres.getValue()
                + "|" + ancientDebris.getValue()
                + "|" + chests.getValue()
                + "|" + spawners.getValue();
    }

    private void rebuildBlockList() {
        Set<Block> blocks = new HashSet<>();
        String mode = preset.getValue();

        if ("All".equals(mode)) {
            Registries.BLOCK.forEach(blocks::add);
            visibleBlocks = Set.copyOf(blocks);
            return;
        }

        boolean custom = "Custom".equals(mode);

        if (custom || "Default".equals(mode) || "All Ores".equals(mode)) {
            addIf(blocks, custom, diamond,
                    Blocks.DIAMOND_ORE,
                    Blocks.DEEPSLATE_DIAMOND_ORE);

            addIf(blocks, custom, iron,
                    Blocks.IRON_ORE,
                    Blocks.DEEPSLATE_IRON_ORE);

            addIf(blocks, custom, gold,
                    Blocks.GOLD_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE);

            addIf(blocks, custom, lapis,
                    Blocks.LAPIS_ORE,
                    Blocks.DEEPSLATE_LAPIS_ORE);

            addIf(blocks, custom, redstone,
                    Blocks.REDSTONE_ORE,
                    Blocks.DEEPSLATE_REDSTONE_ORE);

            addIf(blocks, custom, emerald,
                    Blocks.EMERALD_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE);
        }

        if (custom || "All Ores".equals(mode)) {
            addIf(blocks, custom, copper,
                    Blocks.COPPER_ORE,
                    Blocks.DEEPSLATE_COPPER_ORE);

            addIf(blocks, custom, netherOres,
                    Blocks.NETHER_GOLD_ORE,
                    Blocks.NETHER_QUARTZ_ORE);

            addIf(blocks, custom, ancientDebris,
                    Blocks.ANCIENT_DEBRIS);
        }

        if ("Default".equals(mode) || "All Ores".equals(mode)) {
            blocks.add(Blocks.NETHER_GOLD_ORE);
            blocks.add(Blocks.NETHER_QUARTZ_ORE);
            blocks.add(Blocks.ANCIENT_DEBRIS);
        }

        if (custom) {
            addIf(blocks, true, chests,
                    Blocks.CHEST,
                    Blocks.TRAPPED_CHEST,
                    Blocks.ENDER_CHEST,
                    Blocks.BARREL);

            addIf(blocks, true, spawners,
                    Blocks.SPAWNER,
                    Blocks.TRIAL_SPAWNER);
        }

        visibleBlocks = Set.copyOf(blocks);
    }

    private static void addIf(
            Set<Block> target,
            boolean custom,
            Setting.BoolSetting setting,
            Block... blocks
    ) {
        if (!custom || setting.getValue()) {
            for (Block block : blocks) {
                target.add(block);
            }
        }
    }

    private static void reloadRenderer() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public static boolean isActive() {
        Module module = ModuleManager.getModule("XRay");
        return module != null && module.isEnabled();
    }

    public static boolean isVisible(Block block) {
        if (!isActive()) return true;
        if (block == Blocks.AIR) return true;

        Module module = ModuleManager.getModule("XRay");

        return module instanceof XRay xray
                && xray.visibleBlocks.contains(block);
    }
}
