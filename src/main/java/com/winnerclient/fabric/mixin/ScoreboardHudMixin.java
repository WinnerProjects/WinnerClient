package com.winnerclient.fabric.mixin;

import com.winnerclient.fabric.module.player.NameHider;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Applies WinnerClient NameHider to text drawn by the vanilla sidebar scoreboard.
 *
 * In the target Minecraft/Yarn version the scoreboard sidebar is rendered
 * through InGameHud; ScoreboardHud is not a resolvable Yarn class.
 */
@Mixin(InGameHud.class)
public abstract class ScoreboardHudMixin {
    @ModifyArg(
        method = "renderScoreboardSidebar",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
        ),
        index = 1,
        require = 0
    )
    private Text winnerclient$hideScoreboardNames(Text text) {
        return NameHider.replace(text);
    }
}
