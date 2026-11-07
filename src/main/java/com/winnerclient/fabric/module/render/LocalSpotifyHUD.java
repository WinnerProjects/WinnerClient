package com.winnerclient.fabric.module.render;

import com.winnerclient.fabric.ConfigManager;
import com.winnerclient.fabric.media.AndroidMediaProvider;
import com.winnerclient.fabric.media.MediaControlScreen;
import com.winnerclient.fabric.media.MediaProvider;
import com.winnerclient.fabric.media.MediaSnapshot;
import com.winnerclient.fabric.media.WindowsMediaProvider;
import com.winnerclient.fabric.module.ModuleManager;
import com.winnerclient.fabric.module.Setting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class LocalSpotifyHUD extends SpotifyHUD {
    private final Setting.ModeSetting source = addMode(
            "Source",
            "Media session source",
            "Auto",
            "Auto",
            "System Media",
            "Spotify Desktop"
    );

    // Android bridge: pairing key shown by the SpotifyBridge app. Never logged, never sent over the
    // socket (mutual HMAC challenge-response). Kept as a normal ClickGUI string setting so it can be
    // pasted on touch devices.
    private final Setting.StringSetting bridgeKey = addString(
            "Bridge Key",
            "Pairing key from the SpotifyBridge Android app (keep it private)",
            ""
    );

    // Momentary switch for touch devices: tap it in ClickGUI to open the media control screen.
    private final Setting.BoolSetting openControls = addBool(
            "Open Controls",
            "Tap to open touch media controls",
            false
    );

    private static final Identifier ART_ID =
            Identifier.of("winnerclient", "dynamic/media_art");

    private volatile String selectedSource = "Auto";
    private volatile String selectedKey = "";

    // 0..1 while the progress bar is being dragged, otherwise -1 (render thread + input thread are the same).
    private double seekPreview = -1;

    private MediaProvider provider;
    private ExecutorService artworkWorker;

    private String requestedKey = "";
    private String textureKey = "";

    private int imageWidth;
    private int imageHeight;
    private boolean texturePresent;

    private long generation;
    private long enabledAt;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int barX;
    private int barY;
    private int barW;

    private int previousX;
    private int playX;
    private int nextX;

    private MediaSnapshot visible =
            MediaSnapshot.empty("No media session");

    @Override
    public void onEnable() {
        enabledAt = System.nanoTime();
        generation++;

        artworkWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WinnerClient-AlbumArt");
            thread.setDaemon(true);
            return thread;
        });

        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        selectedKey = bridgeKey.getValue();

        // Momentary switch: a stale "true" (toggled while the module was off) must not pop the
        // screen open the moment the module is enabled.
        openControls.setValue(false);

        if (os.contains("windows")) {
            provider = new WindowsMediaProvider(
                    () -> selectedSource
            );
        } else {
            // Android launchers (Mojo/Pojav-style) run a Linux JVM, so os.name is not reliable here.
            // Any non-Windows host talks to the SpotifyBridge app on 127.0.0.1.
            provider = new AndroidMediaProvider(
                    () -> selectedKey
            );
        }
    }

    @Override
    public void onDisable() {
        shutdownMedia();
    }

    public void shutdownMedia() {
        generation++;

        if (provider != null) {
            provider.close();
            provider = null;
        }

        if (artworkWorker != null) {
            artworkWorker.shutdownNow();
            artworkWorker = null;
        }

        visible = MediaSnapshot.empty("Stopped");
        requestedKey = "";
        clearArtwork();
    }

    @Override
    public void onTick() {
        selectedSource = source.getValue();
        selectedKey = bridgeKey.getValue();

        if (openControls.getValue()) {
            openControls.setValue(false);
            ConfigManager.save();
            openControlsScreen();
        }
    }

    /** Opens the interactive screen (free cursor => taps/clicks reach the HUD, not the game). */
    public void openControlsScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null && isEnabled()) {
            mc.setScreen(new MediaControlScreen(this));
        }
    }

    /** Used by the (optional) key binding. */
    public static void openControls() {
        if (ModuleManager.getModule("SpotifyHUD") instanceof LocalSpotifyHUD hud) {
            hud.openControlsScreen();
        }
    }

    public boolean contains(double x, double y) {
        return panelW > 0
                && x >= panelX && x <= panelX + panelW
                && y >= panelY && y <= panelY + panelH;
    }

    public void handleDrag(double x) {
        if (provider == null
                || !visible.seek()
                || visible.durationMs() <= 0
                || barW <= 0) {
            seekPreview = -1;
            return;
        }

        seekPreview = Math.max(0, Math.min(1, (x - barX) / barW));
    }

    public void cancelSeek() {
        seekPreview = -1;
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        renderPanel(context, -1, -1);
    }

    public void renderPanel(
            DrawContext context,
            int mouseX,
            int mouseY
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return;
        }

        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        MediaSnapshot media = provider == null
                ? MediaSnapshot.empty(
                        os.contains("android")
                                || System.getenv("POJAV_RENDERER") != null
                                ? "Android bridge required"
                                : "Platform not supported"
                )
                : provider.snapshot();

        visible = media;
        queueArtwork(media);

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        if (screenWidth < 90 || screenHeight < 78) {
            return;
        }

        panelW = Math.min(
                Math.max(180, Math.min(500, getInt("Width"))),
                screenWidth - 8
        );

        panelH = Math.min(108, screenHeight - 8);

        switch (getMode("Position")) {
            case "TopLeft" -> {
                panelX = 4;
                panelY = 4;
            }

            case "TopRight" -> {
                panelX = screenWidth - panelW - 4;
                panelY = 4;
            }

            case "BottomLeft" -> {
                panelX = 4;
                panelY = screenHeight - panelH - 4;
            }

            default -> {
                panelX = screenWidth - panelW - 4;
                panelY = screenHeight - panelH - 4;
            }
        }

        int x = panelX;
        int y = panelY;
        int width = panelW;
        int height = panelH;

        float fade = getBool("Animations")
                ? Math.min(
                        1f,
                        Math.max(
                                0f,
                                (System.nanoTime() - enabledAt)
                                        / 400_000_000f
                        )
                )
                : 1f;

        int opacity = Math.max(
                30,
                Math.min(100, getInt("Opacity"))
        );

        int background =
                (opacity * 255 / 100 << 24) | 0x090C13;

        int muted = alpha(0xFFA5ACC2, fade);
        int primary = alpha(0xFFF5F6FA, fade);

        int accent = alpha(
                0xFF000000 | (getColor("Accent") & 0xFFFFFF),
                fade
        );

        rounded(
                context,
                x, y, width, height,
                7,
                alpha(0xFF454B60, fade)
        );

        rounded(
                context,
                x + 1, y + 1,
                width - 2, height - 2,
                6,
                alpha(background, fade)
        );

        int artSize = Math.min(64, height - 36);
        int artX = x + 7;
        int artY = y + 7;

        rounded(
                context,
                artX, artY,
                artSize, artSize,
                4,
                alpha(0xFF252B39, fade)
        );

        TextRenderer textRenderer = mc.textRenderer;

        if (texturePresent && textureKey.equals(media.key())) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    ART_ID,
                    artX,
                    artY,
                    0,
                    0,
                    artSize,
                    artSize,
                    imageWidth,
                    imageHeight,
                    imageWidth,
                    imageHeight,
                    alpha(0xFFFFFFFF, fade)
            );
        } else {
            String placeholder = "♪";

            context.drawText(
                    textRenderer,
                    placeholder,
                    artX + artSize / 2
                            - textRenderer.getWidth(placeholder) / 2,
                    artY + artSize / 2 - 4,
                    muted,
                    false
            );
        }

        int textX = artX + artSize + 9;
        int right = x + width - 8;
        int available = Math.max(0, right - textX);

        context.drawText(
                textRenderer,
                fit(textRenderer, "NOW PLAYING", available),
                textX,
                y + 9,
                alpha(0xFF646D84, fade),
                false
        );

        String title = media.title().isBlank()
                ? "No media"
                : media.title();

        context.drawText(
                textRenderer,
                fit(textRenderer, title, available),
                textX,
                y + 23,
                primary,
                false
        );

        String artist = media.artist().isBlank()
                ? (media.title().isBlank() ? " " : "Unknown artist")
                : media.artist();

        context.drawText(
                textRenderer,
                fit(textRenderer, artist, available),
                textX,
                y + 37,
                muted,
                false
        );

        long position = media.estimatedPosition();
        long duration = media.durationMs();

        if (seekPreview >= 0 && duration > 0) {
            position = (long) (seekPreview * duration);
        }

        String elapsed = position < 0
                ? "--:--"
                : formatTime(position);

        String remaining = duration <= 0 || position < 0
                ? "--:--"
                : "-" + formatTime(
                        Math.max(0, duration - position)
                );

        context.drawText(
                textRenderer,
                elapsed,
                textX,
                y + 52,
                muted,
                false
        );

        context.drawText(
                textRenderer,
                remaining,
                right - textRenderer.getWidth(remaining),
                y + 52,
                muted,
                false
        );

        int middle = (textX + right) / 2;

        previousX = middle - 20;
        playX = middle;
        nextX = middle + 20;

        button(
                context,
                previousX,
                y + 56,
                "|◀",
                media.previous(),
                mouseX,
                mouseY,
                fade
        );

        button(
                context,
                playX,
                y + 56,
                media.playing() ? "Ⅱ" : "▶",
                media.toggle(),
                mouseX,
                mouseY,
                fade
        );

        button(
                context,
                nextX,
                y + 56,
                "▶|",
                media.next(),
                mouseX,
                mouseY,
                fade
        );

        barX = textX;
        barY = y + 70;
        barW = available;

        rounded(
                context,
                barX,
                barY,
                barW,
                3,
                1,
                alpha(0xFF303543, fade)
        );

        if (position >= 0 && duration > 0) {
            int fill = (int) (
                    barW * Math.max(
                            0,
                            Math.min(
                                    1,
                                    (double) position / duration
                            )
                    )
            );

            if (fill > 0) {
                rounded(
                        context,
                        barX,
                        barY,
                        fill,
                        3,
                        1,
                        accent
                );
            }
        }

        if (getBool("Footer")) {
            for (int i = 0; i < 3; i++) {
                int barHeight =
                        getBool("Animations") && media.playing()
                                ? 4 + (int) (
                                        3 * (
                                                1 + Math.sin(
                                                        System.nanoTime()
                                                                / 350_000_000.0
                                                                + i
                                                )
                                        )
                                )
                                : 5;

                context.fill(
                        x + 9 + i * 4,
                        y + height - 12 - barHeight,
                        x + 11 + i * 4,
                        y + height - 12,
                        accent
                );
            }

            context.drawText(
                    textRenderer,
                    fit(textRenderer, media.status(), width - 32),
                    x + 25,
                    y + height - 20,
                    muted,
                    false
            );
        }
    }

    public boolean handleClick(double x, double y) {
        if (provider == null
                || y < panelY
                || y > panelY + panelH) {
            return false;
        }

        if (Math.abs(y - (panelY + 56)) <= 8) {
            if (Math.abs(x - previousX) <= 8
                    && visible.previous()) {

                provider.command(
                        MediaProvider.Command.PREVIOUS,
                        0
                );

            } else if (Math.abs(x - playX) <= 8
                    && visible.toggle()) {

                provider.command(
                        MediaProvider.Command.TOGGLE,
                        0
                );

            } else if (Math.abs(x - nextX) <= 8
                    && visible.next()) {

                provider.command(
                        MediaProvider.Command.NEXT,
                        0
                );
            }
        }

        return visible.seek()
                && visible.durationMs() > 0
                && x >= barX
                && x <= barX + barW
                && Math.abs(y - barY) <= 7;
    }

    public void handleSeek(double x) {
        seekPreview = -1;

        if (provider == null
                || !visible.seek()
                || visible.durationMs() <= 0
                || barW <= 0) {
            return;
        }

        double fraction = Math.max(
                0,
                Math.min(1, (x - barX) / barW)
        );

        long target = (long) (
                fraction * visible.durationMs()
        );

        provider.command(
                MediaProvider.Command.SEEK,
                target
        );
    }

    private void queueArtwork(MediaSnapshot media) {
        if (media.key().equals(requestedKey)) {
            return;
        }

        requestedKey = media.key();
        clearArtwork();

        byte[] bytes = media.artwork();

        if (bytes == null || artworkWorker == null) {
            return;
        }

        String key = media.key();
        long currentGeneration = generation;

        try {
            artworkWorker.execute(() -> {
                NativeImage image;

                try {
                    image = NativeImage.read(
                            new ByteArrayInputStream(bytes)
                    );
                } catch (Exception ignored) {
                    return;
                }

                MinecraftClient.getInstance().execute(() -> {
                    if (generation != currentGeneration
                            || !requestedKey.equals(key)
                            || !isEnabled()) {

                        image.close();
                        return;
                    }

                    if (image.getWidth() > 4096
                            || image.getHeight() > 4096) {

                        image.close();
                        return;
                    }

                    clearArtwork();

                    imageWidth = image.getWidth();
                    imageHeight = image.getHeight();

                    NativeImageBackedTexture texture =
                            new NativeImageBackedTexture(
                                    () -> "WinnerClient album art",
                                    image
                            );

                    MinecraftClient.getInstance()
                            .getTextureManager()
                            .registerTexture(ART_ID, texture);

                    texture.upload();

                    texturePresent = true;
                    textureKey = key;
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void clearArtwork() {
        if (texturePresent) {
            MinecraftClient.getInstance()
                    .getTextureManager()
                    .destroyTexture(ART_ID);
        }

        texturePresent = false;
        textureKey = "";
    }

    private static void button(
            DrawContext context,
            int x,
            int y,
            String label,
            boolean enabled,
            int mouseX,
            int mouseY,
            float fade
    ) {
        TextRenderer textRenderer =
                MinecraftClient.getInstance().textRenderer;

        boolean hover = enabled
                && Math.abs(mouseX - x) <= 8
                && Math.abs(mouseY - y) <= 8;

        if (hover) {
            rounded(
                    context,
                    x - 8,
                    y - 7,
                    16,
                    14,
                    4,
                    alpha(0xFF31394B, fade)
            );
        }

        context.drawText(
                textRenderer,
                label,
                x - textRenderer.getWidth(label) / 2,
                y - 4,
                alpha(
                        enabled ? 0xFFF5F6FA : 0xFF505767,
                        fade
                ),
                false
        );
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;

        return (seconds / 60)
                + ":"
                + (seconds % 60 < 10 ? "0" : "")
                + (seconds % 60);
    }

    private static String fit(
            TextRenderer renderer,
            String text,
            int maxWidth
    ) {
        if (maxWidth <= 0) {
            return "";
        }

        if (renderer.getWidth(text) <= maxWidth) {
            return text;
        }

        if (maxWidth < renderer.getWidth("...")) {
            return "";
        }

        return renderer.trimToWidth(
                text,
                maxWidth - renderer.getWidth("...")
        ) + "...";
    }

    private static int alpha(int color, float fade) {
        return (
                Math.round(
                        ((color >>> 24) & 255) * fade
                ) << 24
        ) | (color & 0xFFFFFF);
    }

    private static void rounded(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int radius,
            int color
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }

        radius = Math.max(
                0,
                Math.min(radius, Math.min(width, height) / 2)
        );

        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;

            int inset = (int) Math.ceil(
                    radius - Math.sqrt(
                            Math.max(
                                    0,
                                    radius * radius - dy * dy
                            )
                    )
            );

            context.fill(
                    x + inset,
                    y + row,
                    x + width - inset,
                    y + row + 1,
                    color
            );

            context.fill(
                    x + inset,
                    y + height - row - 1,
                    x + width - inset,
                    y + height - row,
                    color
            );
        }

        context.fill(
                x,
                y + radius,
                x + width,
                y + height - radius,
                color
        );
    }
          }
