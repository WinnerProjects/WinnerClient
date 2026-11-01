package com.winnerclient.fabric.module;

import net.minecraft.block.Block;
import java.util.Set;

public interface BlockSelection {
    String getName();

    Set<String> getSelectedIds();

    boolean isSelected(Block block);

    void toggleBlock(Block block);
}
