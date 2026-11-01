package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class XRay extends SelectedBlocksModule {
    private static final String DEFAULT_BLOCKS = String.join(",",
        "minecraft:coal_ore",
        "minecraft:deepslate_coal_ore",
        "minecraft:iron_ore",
        "minecraft:deepslate_iron_ore",
        "minecraft:gold_ore",
        "minecraft:deepslate_gold_ore",
        "minecraft:lapis_ore",
        "minecraft:deepslate_lapis_ore",
        "minecraft:redstone_ore",
        "minecraft:deepslate_redstone_ore",
        "minecraft:diamond_ore",
        "minecraft:deepslate_diamond_ore",
        "minecraft:emerald_ore",
        "minecraft:deepslate_emerald_ore",
        "minecraft:copper_ore",
        "minecraft:deepslate_copper_ore",
        "minecraft:nether_gold_ore",
        "minecraft:nether_quartz_ore",
        "minecraft:ancient_debris"
    );

    public XRay() {
        super(
            "XRay",
            "Only render selected blocks",
            DEFAULT_BLOCKS
        );
    }

    @Override
    public void onEnable() {
        Module reverse = ModuleManager.getModule("ReverseXRay");

        if (reverse != null && reverse.isEnabled()) {
            reverse.setEnabled(false);
        }

        syncSelection(false);
        reloadWorldRenderer();
    }

    @Override
    public void onDisable() {
        reloadWorldRenderer();
    }

    public static boolean isActive() {
        Module module = ModuleManager.getModule("XRay");

        return module instanceof XRay && module.isEnabled();
    }

    public static boolean isSelectedForRendering(Block block) {
        Module module = ModuleManager.getModule("XRay");

        return module instanceof XRay xray
            && xray.isEnabled()
            && xray.containsForRendering(block);
    }

    /**
     * Eski XRay mixin'leriyle uyumluluk.
     */
    public static boolean isVisible(Block block) {
        if (!isActive()) {
            return true;
        }

        if (block == Blocks.AIR
            || block == Blocks.CAVE_AIR
            || block == Blocks.VOID_AIR) {
            return true;
        }

        return isSelectedForRendering(block);
    }
}
