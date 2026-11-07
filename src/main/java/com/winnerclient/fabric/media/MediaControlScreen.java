package com.winnerclient.fabric.media;

import com.winnerclient.fabric.module.render.LocalSpotifyHUD;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class MediaControlScreen extends Screen {
    private final LocalSpotifyHUD hud;
    private boolean seeking;

    public MediaControlScreen(LocalSpotifyHUD hud) {
        super(Text.literal("Media controls"));
        this.hud = hud;
    }

    @Override
    public void render(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        hud.renderPanel(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.getKeycode() == 0) {
            // Touch devices have no Escape key: tapping outside the panel closes the screen.
            if (!hud.contains(click.x(), click.y())) {
                close();
                return true;
            }

            seeking = hud.handleClick(click.x(), click.y());

            if (seeking) {
                hud.handleDrag(click.x());
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(
            Click click,
            double deltaX,
            double deltaY
    ) {
        if (seeking) {
            hud.handleDrag(click.x());
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (seeking) {
            hud.handleSeek(click.x());
        }

        seeking = false;
        return true;
    }

    @Override
    public void removed() {
        hud.cancelSeek();
        seeking = false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
