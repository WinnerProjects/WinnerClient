package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.player.Freecam;
import com.winnerclient.fabric.module.render.ReverseXRay;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

@Mixin(ChunkRendererRegion.class)
public class ReverseXRayRegionMixin {

    /*
     * Komşuları incelerken getBlockState tekrar bu mixin'e girer.
     * Bu bayrak ham blok durumunu okumamızı sağlar.
     */
    @Unique
    private static final ThreadLocal<Boolean> winnerclient$rawLookup =
            ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "getBlockState",
            at = @At("RETURN"),
            cancellable = true
    )
    private void winnerclient$replaceRenderBlock(
            BlockPos pos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (winnerclient$rawLookup.get()) return;

        BlockState original = cir.getReturnValue();
        if (original == null) return;

        // ReverseXRay: seçili blokları stone/deepslate olarak göster.
        BlockState reverseReplacement =
                ReverseXRay.getReplacement(original.getBlock(), pos);

        if (reverseReplacement != null) {
            cir.setReturnValue(reverseReplacement);
            return;
        }

        // Freecam: sadece küçük, birbirine bağlı hava ceplerini doldur.
        if (!Freecam.isAntiFreecamBypassActive()) return;
        if (!original.isAir()) return;

        ChunkRendererRegion region =
                (ChunkRendererRegion) (Object) this;

        if (winnerclient$isSmallAirPocket(region, pos)) {
            cir.setReturnValue(winnerclient$fillerFor(pos));
        }
    }

    @Unique
    private static boolean winnerclient$isSmallAirPocket(
            ChunkRendererRegion region,
            BlockPos start
    ) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos first = start.toImmutable();

        queue.add(first);
        visited.add(first);

        winnerclient$rawLookup.set(true);

        try {
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();

                for (Direction direction : Direction.values()) {
                    BlockPos next = current.offset(direction);

                    /*
                     * Dünya yüksekliği dışını kapalı boşluk sayma.
                     */
                    if (next.getY() < region.getBottomY()
                            || next.getY() > region.getTopYInclusive()) {
                        return false;
                    }

                    if (visited.contains(next)) continue;

                    BlockState neighbor;

                    try {
                        neighbor = region.getBlockState(next);
                    } catch (IndexOutOfBoundsException ex) {
                        /*
                         * Render bölgesi dışında kalan boşluk için
                         * eksik veriden karar verme.
                         */
                        return false;
                    }

                    if (neighbor == null) return false;
                    if (!neighbor.isAir()) continue;

                    BlockPos immutable = next.toImmutable();
                    visited.add(immutable);

                    // Altı veya daha fazla hava bloğuysa doldurma.
                    if (visited.size() >= 6) {
                        return false;
                    }

                    queue.addLast(immutable);
                }
            }

            // Buraya ulaşırsa bağlı hava bloğu sayısı 1-5.
            return true;
        } finally {
            winnerclient$rawLookup.remove();
        }
    }

    @Unique
    private static BlockState winnerclient$fillerFor(BlockPos pos) {
        if (pos.getY() > 0) {
            return Blocks.STONE.getDefaultState();
        }

        if (pos.getY() < 0) {
            return Blocks.DEEPSLATE.getDefaultState();
        }

        // Y = 0: aynı konum her çizimde aynı bloğu seçsin.
        long hash = pos.asLong();
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;

        return (hash & 1L) == 0L
                ? Blocks.STONE.getDefaultState()
                : Blocks.DEEPSLATE.getDefaultState();
    }
}
