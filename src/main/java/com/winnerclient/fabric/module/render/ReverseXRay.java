package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class ReverseXRay extends SelectedBlocksModule {
    public ReverseXRay() {
        super(
            "ReverseXRay",
            "Render selected blocks as stone or deepslate",
            ""
        );
    }

    @Override
    public void onEnable() {
        // İki modülün render kuralları çakışmasın.
        Module xray = ModuleManager.getModule("XRay");

        if (xray != null && xray.isEnabled()) {
            xray.setEnabled(false);
        }

        syncSelection(false);
        reloadWorldRenderer();
    }

    @Override
    public void onDisable() {
        reloadWorldRenderer();
    }

    public static boolean isActive() {
        Module module = ModuleManager.getModule("ReverseXRay");

        return module instanceof ReverseXRay
            && module.isEnabled();
    }

    public static BlockState getReplacement(
        Block originalBlock,
        BlockPos pos
    ) {
        Module module = ModuleManager.getModule("ReverseXRay");

        if (!(module instanceof ReverseXRay reverse)
            || !reverse.isEnabled()
            || !reverse.containsForRendering(originalBlock)) {
            return null;
        }

        if (pos.getY() > 0) {
            return Blocks.STONE.getDefaultState();
        }

        if (pos.getY() < 0) {
            return Blocks.DEEPSLATE.getDefaultState();
        }

        // Y = 0 için konuma bağlı rastgele seçim.
        // Aynı konum her çizimde aynı sonucu verir.
        long hash = pos.asLong();
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;

        return (hash & 1L) == 0L
            ? Blocks.STONE.getDefaultState()
            : Blocks.DEEPSLATE.getDefaultState();
    }
}
