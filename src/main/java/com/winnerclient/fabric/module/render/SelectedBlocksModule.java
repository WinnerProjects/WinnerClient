package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.ConfigManager;
import com.winnerclient.fabric.module.BlockSelection;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public abstract class SelectedBlocksModule extends Module
    implements BlockSelection {

    private final Setting.StringSetting blocks;

    // Render sırasında okunacak listelerin değişmez kopyaları.
    private volatile Set<Block> selectedBlocks = Set.of();
    private volatile Set<String> selectedIds = Set.of();

    private String cachedValue;

    protected SelectedBlocksModule(
        String name,
        String description,
        String defaultBlocks
    ) {
        super(name, description, Category.RENDER);

        blocks = addString(
            "Blocks",
            "Selected blocks",
            defaultBlocks
        );

        syncSelection(false);
    }

    @Override
    public Set<String> getSelectedIds() {
        syncSelection(false);
        return selectedIds;
    }

    @Override
    public boolean isSelected(Block block) {
        syncSelection(false);
        return selectedBlocks.contains(block);
    }

    /**
     * Chunk render kodu bu metodu kullanır.
     */
    public boolean containsForRendering(Block block) {
        return selectedBlocks.contains(block);
    }

    @Override
    public void toggleBlock(Block block) {
        syncSelection(false);

        String id = Registries.BLOCK.getId(block).toString();

        Set<String> updated = new TreeSet<>(selectedIds);

        if (!updated.add(id)) {
            updated.remove(id);
        }

        blocks.setValue(String.join(",", updated));

        syncSelection(true);
        ConfigManager.save();
    }

    @Override
    public void onTick() {
        // Config'den yüklenen veya değiştirilen seçimleri güncelle.
        syncSelection(true);
    }

    protected void syncSelection(boolean reloadIfChanged) {
        String value = blocks.getValue();

        if (value == null) {
            value = "";
        }

        if (value.equals(cachedValue)) {
            return;
        }

        cachedValue = value;

        Set<String> ids = new HashSet<>();

        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .forEach(ids::add);

        Set<Block> resolvedBlocks = new HashSet<>();

        Registries.BLOCK.forEach(block -> {
            String id = Registries.BLOCK.getId(block).toString();

            if (ids.contains(id)) {
                resolvedBlocks.add(block);
            }
        });

        selectedIds = Collections.unmodifiableSet(ids);
        selectedBlocks = Collections.unmodifiableSet(resolvedBlocks);

        if (reloadIfChanged && isEnabled()) {
            reloadWorldRenderer();
        }
    }

    protected static void reloadWorldRenderer() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && mc.worldRenderer != null) {
            mc.execute(() -> {
                if (mc.world != null) {
                    mc.worldRenderer.reload();
                }
            });
        }
    }
    }
