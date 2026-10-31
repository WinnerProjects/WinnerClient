package com.winnerclient.fabric.module.misc;

import com.winnerclient.fabric.WinnerClientMod;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ChunkFinder extends Module {
    private final Setting.BoolSetting autoScan = addBool(
        "Auto Scan", "Scan nearby loaded chunks", true
    );

    private final Setting.BoolSetting small = addBool(
        "Small Bud", "Accept small buds", true
    );

    private final Setting.BoolSetting medium = addBool(
        "Medium Bud", "Accept medium buds", true
    );

    private final Setting.BoolSetting large = addBool(
        "Large Bud", "Accept large buds", true
    );

    private final Setting.BoolSetting cluster = addBool(
        "Amethyst Cluster", "Accept clusters", true
    );

    private final Setting.IntSetting accuracy = addInt(
        "Min Accuracy", "Minimum heuristic confidence score", 60, 0, 100
    );

    private final Setting.NumberSetting density = addNum(
        "Minimum Density",
        "Selected growth / amethyst blocks, percent",
        1.0, 0.0, 100.0, 1.0
    );

    private final Setting.BoolSetting highlight = addBool(
        "Chunk Highlight", "Enable chunk markers", true
    );

    private final Setting.BoolSetting showColors = addBool(
        "Show Chunk Colors", "Show markers and coordinate overlay", true
    );

    private final Setting.IntSetting opacity = addInt(
        "Opacity", "Marker opacity percent", 65, 5, 100
    );

    private final Setting.BoolSetting saveChunks = addBool(
        "Save Chunks", "Export matches once; switch resets", false
    );

    private final Setting.BoolSetting rescan = addBool(
        "Rescan", "Clear cached results and scan again", false
    );

    private final Setting.BoolSetting coordinates = addBool(
        "Show Coordinates", "Show matching chunk coordinates", true
    );

    private final Setting.IntSetting radius = addInt(
        "Chunk Radius", "Loaded chunk scan radius", 4, 1, 8
    );

    private static final int BUDGET = 2048;
    private static final int R = 8;
    private static final int SIDE = 2 * R + 1;
    private static final int MAX_SEEDS = 64;

    private final Map<Long, Result> cache = new LinkedHashMap<>();
    private final Set<Long> dirty = new LinkedHashSet<>();

    private Object currentWorld;
    private int tick;
    private Job job;
    private boolean manual;

    public ChunkFinder() {
        super(
            "ChunkFinder",
            "Find likely amethyst geodes in loaded Overworld chunks",
            Category.MISC
        );
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int kx(long key) {
        return (int) (key >> 32);
    }

    private static int kz(long key) {
        return (int) key;
    }

    /**
     * Blok veya chunk verisi değiştiğinde istemci thread'inde çağrılır.
     */
    public void changed(int chunkX, int chunkZ) {
        if (!isEnabled()) return;

        // Jeotun incelenen çevresi komşu chunk'lara taşabilir.
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                long key = key(x, z);

                if (
                    cache.remove(key) != null ||
                    (job != null && job.key == key)
                ) {
                    dirty.add(key);
                }
            }
        }

        if (
            job != null &&
            Math.abs(kx(job.key) - chunkX) <= 1 &&
            Math.abs(kz(job.key) - chunkZ) <= 1
        ) {
            dirty.add(job.key);
            job = null;
        }
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @Override
    public void onDisconnect() {
        clear();
    }

    private void clear() {
        cache.clear();
        dirty.clear();
        job = null;
        manual = false;
        currentWorld = null;
    }

    private static WorldChunk loaded(
        MinecraftClient mc,
        int chunkX,
        int chunkZ
    ) {
        return mc.world.getChunkManager().getChunk(
            chunkX,
            chunkZ,
            ChunkStatus.FULL,
            false
        );
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null) {
            clear();
            return;
        }

        if (currentWorld != mc.world) {
            clear();
            currentWorld = mc.world;
        }

        if (!mc.world.getRegistryKey().equals(World.OVERWORLD)) {
            cache.clear();
            dirty.clear();
            job = null;
            manual = false;
            return;
        }

        int playerChunkX = mc.player.getBlockPos().getX() >> 4;
        int playerChunkZ = mc.player.getBlockPos().getZ() >> 4;
        int scanRadius = radius.getValue();

        if (rescan.getValue()) {
            cache.clear();
            dirty.clear();
            job = null;
            manual = true;
            rescan.setValue(false);
        }

        cache.keySet().removeIf(key ->
            Math.abs(kx(key) - playerChunkX) > scanRadius ||
            Math.abs(kz(key) - playerChunkZ) > scanRadius ||
            loaded(mc, kx(key), kz(key)) == null
        );

        dirty.removeIf(key ->
            Math.abs(kx(key) - playerChunkX) > scanRadius ||
            Math.abs(kz(key) - playerChunkZ) > scanRadius ||
            loaded(mc, kx(key), kz(key)) == null
        );

        if (
            job != null &&
            (
                Math.abs(kx(job.key) - playerChunkX) > scanRadius ||
                Math.abs(kz(job.key) - playerChunkZ) > scanRadius ||
                loaded(mc, kx(job.key), kz(job.key)) == null
            )
        ) {
            job = null;
        }

        if (
            (autoScan.getValue() || manual) &&
            (++tick % 20 == 1 || dirty.isEmpty())
        ) {
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    int cx = playerChunkX + dx;
                    int cz = playerChunkZ + dz;
                    long key = key(cx, cz);

                    if (
                        !cache.containsKey(key) &&
                        (job == null || job.key != key) &&
                        loaded(mc, cx, cz) != null
                    ) {
                        dirty.add(key);
                    }
                }
            }
        }

        if (
            (autoScan.getValue() || manual) &&
            job == null &&
            !dirty.isEmpty()
        ) {
            long key = dirty.iterator().next();
            dirty.remove(key);

            if (loaded(mc, kx(key), kz(key)) != null) {
                job = new Job(
                    key,
                    mc.world.getBottomY(),
                    mc.world.getTopYInclusive()
                );
            }
        }

        if ((autoScan.getValue() || manual) && job != null) {
            if (job.advance(mc, BUDGET)) {
                cache.put(job.key, new Result(job.candidates));
                job = null;
            }
        }

        if (manual && job == null && dirty.isEmpty()) {
            manual = false;
        }

        if (saveChunks.getValue()) {
            saveChunks.setValue(false);
            exportMatches(mc);
        }
    }

    private static boolean seed(Block block) {
        return block == Blocks.BUDDING_AMETHYST
            || block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.AMETHYST_CLUSTER;
    }

    private static final class Job {
        final long key;
        final int bottom;
        final int top;

        final List<BlockPos> seeds = new ArrayList<>();
        final List<Candidate> candidates = new ArrayList<>();

        int cursor;
        int seedIndex;
        int neighbourhoodIndex;

        boolean incomplete;
        Candidate building;

        Job(long key, int bottom, int top) {
            this.key = key;
            this.bottom = bottom;
            this.top = top;
        }

        boolean advance(MinecraftClient mc, int budget) {
            int chunkX = kx(key);
            int chunkZ = kz(key);

            WorldChunk home = loaded(mc, chunkX, chunkZ);

            if (home == null) {
                return true;
            }

            int scanSize = (top - bottom + 1) * 256;
            BlockPos.Mutable pos = new BlockPos.Mutable();

            while (budget > 0 && cursor < scanSize) {
                int i = cursor++;

                pos.set(
                    (chunkX << 4) + (i & 15),
                    bottom + (i >> 8),
                    (chunkZ << 4) + ((i >> 4) & 15)
                );

                Block block = home.getBlockState(pos).getBlock();

                if (seed(block)) {
                    boolean near = false;

                    for (BlockPos old : seeds) {
                        if (old.getSquaredDistance(pos) <= 36) {
                            near = true;
                            break;
                        }
                    }

                    if (!near) {
                        if (seeds.size() < MAX_SEEDS) {
                            seeds.add(pos.toImmutable());
                        } else {
                            incomplete = true;
                        }
                    }
                }

                budget--;
            }

            if (cursor < scanSize) {
                return false;
            }

            while (budget > 0 && seedIndex < seeds.size()) {
                BlockPos center = seeds.get(seedIndex);

                if (building == null) {
                    building = new Candidate(center.getY());
                    neighbourhoodIndex = 0;
                }

                while (
                    budget > 0 &&
                    neighbourhoodIndex < SIDE * SIDE * SIDE
                ) {
                    int i = neighbourhoodIndex++;

                    int dx = i % SIDE - R;
                    int dz = (i / SIDE) % SIDE - R;
                    int dy = i / (SIDE * SIDE) - R;

                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;

                    budget--;

                    if (y < bottom || y > top) {
                        building.complete = false;
                        continue;
                    }

                    WorldChunk chunk = loaded(mc, x >> 4, z >> 4);

                    if (chunk == null) {
                        building.complete = false;
                        continue;
                    }

                    pos.set(x, y, z);

                    building.observe(
                        chunk.getBlockState(pos).getBlock(),
                        dx,
                        dy,
                        dz
                    );
                }

                if (neighbourhoodIndex == SIDE * SIDE * SIDE) {
                    if (incomplete) {
                        building.complete = false;
                    }

                    candidates.add(building);
                    building = null;
                    seedIndex++;
                }
            }

            return seedIndex >= seeds.size();
        }
    }

    private static final class Candidate {
        final int y;

        boolean complete = true;

        int small;
        int medium;
        int large;
        int cluster;

        int amethyst;
        int budding;
        int calcite;
        int basalt;

        int hostNear;
        int calciteOuter;
        int basaltOuter;

        int buddingSectors;
        int budSectors;
        int calciteSectors;
        int basaltSectors;

        Candidate(int y) {
            this.y = y;
        }

        void observe(Block block, int x, int y, int z) {
            int distanceSquared = x * x + y * y + z * z;

            if (distanceSquared > R * R) {
                return;
            }

            int sector =
                (x >= 0 ? 1 : 0) |
                (y >= 0 ? 2 : 0) |
                (z >= 0 ? 4 : 0);

            int mask = 1 << sector;

            if (block == Blocks.AMETHYST_BLOCK) {
                amethyst++;

                if (distanceSquared <= 49) {
                    hostNear++;
                }
            } else if (block == Blocks.BUDDING_AMETHYST) {
                budding++;
                buddingSectors |= mask;

                if (distanceSquared <= 49) {
                    hostNear++;
                }
            } else if (block == Blocks.CALCITE) {
                calcite++;
                calciteSectors |= mask;

                if (distanceSquared >= 9) {
                    calciteOuter++;
                }
            } else if (block == Blocks.SMOOTH_BASALT) {
                basalt++;
                basaltSectors |= mask;

                if (distanceSquared >= 16) {
                    basaltOuter++;
                }
            } else if (block == Blocks.SMALL_AMETHYST_BUD) {
                small++;
                budSectors |= mask;
            } else if (block == Blocks.MEDIUM_AMETHYST_BUD) {
                medium++;
                budSectors |= mask;
            } else if (block == Blocks.LARGE_AMETHYST_BUD) {
                large++;
                budSectors |= mask;
            } else if (block == Blocks.AMETHYST_CLUSTER) {
                cluster++;
                budSectors |= mask;
            }
        }

        int confidence() {
            double host = Math.min(
                1.0,
                (amethyst + budding) / 32.0
            );

            double buddingScore = Math.min(1.0, budding / 4.0);
            double calciteScore = Math.min(1.0, calcite / 24.0);
            double basaltScore = Math.min(1.0, basalt / 24.0);

            double distribution =
                (
                    Integer.bitCount(calciteSectors) +
                    Integer.bitCount(basaltSectors)
                ) / 16.0;

            double location = calcite == 0 || basalt == 0
                ? 0.0
                : 0.5 * calciteOuter / calcite
                    + 0.5 * basaltOuter / basalt;

            double core = amethyst + budding == 0
                ? 0.0
                : hostNear / (double) (amethyst + budding);

            double budDistribution =
                (
                    Integer.bitCount(buddingSectors) +
                    Integer.bitCount(budSectors)
                ) / 16.0;

            double diversity =
                (amethyst > 0 ? 0.25 : 0.0) +
                (budding > 0 ? 0.25 : 0.0) +
                (calcite > 0 ? 0.25 : 0.0) +
                (basalt > 0 ? 0.25 : 0.0);

            return (int) Math.round(
                20 * host +
                15 * buddingScore +
                10 * calciteScore +
                10 * basaltScore +
                15 * distribution +
                10 * location +
                5 * core +
                5 * budDistribution +
                10 * diversity
            );
        }
    }

    private record Result(List<Candidate> candidates) {
        Result {
            candidates = List.copyOf(candidates);
        }
    }

    private int selected(Candidate candidate) {
        return
            (small.getValue() ? candidate.small : 0) +
            (medium.getValue() ? candidate.medium : 0) +
            (large.getValue() ? candidate.large : 0) +
            (cluster.getValue() ? candidate.cluster : 0);
    }

    private boolean passes(Candidate candidate) {
        int selected = selected(candidate);
        int host = candidate.amethyst + candidate.budding;

        return candidate.complete &&
            host > 0 &&
            selected > 0 &&
            selected * 100.0 / host >= density.getValue() &&
            candidate.confidence() >= accuracy.getValue();
    }

    private Candidate best(Result result) {
        Candidate best = null;

        for (Candidate candidate : result.candidates()) {
            if (
                passes(candidate) &&
                (
                    best == null ||
                    candidate.confidence() > best.confidence()
                )
            ) {
                best = candidate;
            }
        }

        return best;
    }

    private int color(int score) {
        int rgb =
            score < 20 ? 0x28B848 :
            score < 40 ? 0x9DEA6B :
            score < 60 ? 0xFFDB35 :
            score < 80 ? 0xFF941A :
            0xFF3444;

        int alpha = (int) Math.round(
            Math.max(5, Math.min(100, opacity.getValue()))
                * 255.0 / 100
        );

        return (alpha << 24) | rgb;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (
            mc.world == null ||
            mc.player == null ||
            !highlight.getValue() ||
            !showColors.getValue()
        ) {
            return;
        }

        var worldRenderer = context.worldRenderer();

        if (worldRenderer == null) {
            return;
        }

        try (var scope = worldRenderer.startDrawingGizmos()) {
            for (var entry : cache.entrySet()) {
                Candidate candidate = best(entry.getValue());

                if (
                    candidate == null ||
                    loaded(
                        mc,
                        kx(entry.getKey()),
                        kz(entry.getKey())
                    ) == null
                ) {
                    continue;
                }

                int x = kx(entry.getKey()) << 4;
                int z = kz(entry.getKey()) << 4;

                Box box = new Box(
                    x,
                    candidate.y,
                    z,
                    x + 16,
                    candidate.y + 1,
                    z + 16
                );

                GizmoDrawing.box(
                    box,
                    DrawStyle.stroked(
                        color(candidate.confidence()),
                        1.5f
                    )
                ).ignoreOcclusion();
            }
        }
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        if (
            !highlight.getValue() ||
            !showColors.getValue() ||
            !coordinates.getValue()
        ) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null) {
            return;
        }

        int y = 24;
        int shown = 0;

        for (var entry : cache.entrySet()) {
            Candidate candidate = best(entry.getValue());

            if (
                candidate == null ||
                loaded(
                    mc,
                    kx(entry.getKey()),
                    kz(entry.getKey())
                ) == null
            ) {
                continue;
            }

            String line =
                "Chunk " + kx(entry.getKey()) +
                ", " + kz(entry.getKey()) +
                " | Y " + candidate.y +
                " | " + candidate.confidence() + "/100";

            context.drawTextWithShadow(
                mc.textRenderer,
                line,
                6,
                y,
                color(candidate.confidence())
            );

            y += 10;

            if (++shown >= 8) {
                break;
            }
        }
    }

    private void exportMatches(MinecraftClient mc) {
        List<String> lines = new ArrayList<>();
        lines.add("chunk_x,chunk_z,block_x,block_y,block_z,confidence");

        for (var entry : cache.entrySet()) {
            Candidate candidate = best(entry.getValue());

            if (
                candidate != null &&
                loaded(
                    mc,
                    kx(entry.getKey()),
                    kz(entry.getKey())
                ) != null
            ) {
                lines.add(
                    kx(entry.getKey()) + "," +
                    kz(entry.getKey()) + "," +
                    (kx(entry.getKey()) << 4) + "," +
                    candidate.y + "," +
                    (kz(entry.getKey()) << 4) + "," +
                    candidate.confidence()
                );
            }
        }

        Path directory = mc.runDirectory.toPath()
            .resolve("config")
            .resolve("winnerclient");

        // Ayrı dünyaların sonuçları yanlışlıkla birbirine yazılmasın.
        Path file = directory.resolve(
            "chunkfinder-" + UUID.randomUUID() + ".csv"
        );

        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                Files.write(file, lines, StandardCharsets.UTF_8);
            } catch (Exception e) {
                WinnerClientMod.LOGGER.error(
                    "ChunkFinder export failed",
                    e
                );
            }
        });
    }
              }
