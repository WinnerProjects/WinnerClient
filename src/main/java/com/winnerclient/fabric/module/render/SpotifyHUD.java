package com.winnerclient.fabric.module.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Existing SpotifyHUD settings and visual implementation.
 * LocalSpotifyHUD extends this class and overrides its lifecycle.
 */
public class SpotifyHUD extends Module {
    private final Setting.ModeSetting position = addMode(
            "Position",
            "Overlay position",
            "BottomRight",
            "TopLeft",
            "TopRight",
            "BottomLeft",
            "BottomRight"
    );

    private final Setting.IntSetting width = addInt(
            "Width",
            "Preferred panel width",
            300,
            180,
            500
    );

    private final Setting.IntSetting opacity = addInt(
            "Opacity",
            "Background opacity percent",
            90,
            30,
            100
    );

    private final Setting.ColorSetting accent = addColor(
            "Accent",
            "Progress and waveform color",
            0xFF939DBE,
            false
    );

    private final Setting.BoolSetting footer = addBool(
            "Footer",
            "Show media status",
            true
    );

    private final Setting.BoolSetting animations = addBool(
            "Animations",
            "Animate fade-in and waveform",
            true
    );

    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile int positionMs;
    private volatile int durationMs;
    private volatile boolean hasProgress;
    private volatile boolean spotifyRunning;
    private volatile boolean running;

    private Thread pollingThread;
    private long enabledAt;

    public SpotifyHUD() {
        super("SpotifyHUD", "Now playing overlay", Category.RENDER);
    }

    @Override
    public void onEnable() {
        enabledAt = System.nanoTime();
        running = true;

        pollingThread = new Thread(this::pollLoop, "SpotifyHUD");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    @Override
    public void onDisable() {
        running = false;

        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }

        currentTitle = "";
        currentArtist = "";
        positionMs = 0;
        durationMs = 0;
        hasProgress = false;
        spotifyRunning = false;
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                fetchTitle();
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void fetchTitle() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "$OutputEncoding = [Console]::OutputEncoding = "
                            + "[System.Text.Encoding]::UTF8; "
                            + "(Get-Process -Name 'spotify','Spotify' "
                            + "-ErrorAction SilentlyContinue | "
                            + "Where-Object { $_.MainWindowTitle -ne '' } | "
                            + "Select-Object -First 1).MainWindowTitle"
            });

            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    p.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {
                String line = reader.readLine();
                p.waitFor();

                if (line == null || line.isBlank()) {
                    currentTitle = "";
                    currentArtist = "";
                    spotifyRunning = false;
                    return;
                }

                String title = line.trim();

                if (title.endsWith(" - Spotify")) {
                    title = title.substring(
                            0,
                            title.length() - " - Spotify".length()
                    );
                }

                int separator = title.indexOf(" - ");

                if (separator > 0
                        && separator < title.length() - 3) {
                    currentArtist = title.substring(0, separator).trim();
                    currentTitle = title.substring(separator + 3).trim();
                } else {
                    currentTitle = title;
                    currentArtist = "";
                }

                spotifyRunning = true;
            }
        } catch (Exception ignored) {
            currentTitle = "";
            currentArtist = "";
            spotifyRunning = false;
        }
    }

    // Legacy local helper. LocalSpotifyHUD does not call this lifecycle.
    private void fetchProgress() {
        try {
            Path helperPath = Paths.get(
                    System.getenv("APPDATA"),
                    "Spotify",
                    "spotify-helper.json"
            );

            if (!Files.exists(helperPath)) {
                return;
            }

            JsonObject helper = JsonParser
                    .parseString(Files.readString(helperPath))
                    .getAsJsonObject();

            fetchFromLocalApi(
                    helper.get("port").getAsInt(),
                    helper.get("token").getAsString()
            );
        } catch (Exception ignored) {
        }
    }

    private void fetchFromLocalApi(int port, String token)
            throws Exception {

        String[] paths = {
                "/state.json",
                "/remote/state.json"
        };

        for (String path : paths) {
            for (boolean useAuth : new boolean[]{true, false}) {
                HttpURLConnection conn = null;

                try {
                    URI uri = new URI(
                            "http://127.0.0.1:" + port + path
                    );

                    conn = (HttpURLConnection) uri.toURL()
                            .openConnection();

                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(1500);
                    conn.setReadTimeout(1500);

                    if (useAuth) {
                        conn.setRequestProperty(
                                "Origin",
                                "https://open.spotify.com"
                        );

                        conn.setRequestProperty(
                                "Authorization",
                                "Bearer " + token
                        );
                    }

                    if (conn.getResponseCode() != 200) {
                        continue;
                    }

                    StringBuilder body = new StringBuilder();

                    try (
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(
                                            conn.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
                    ) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            body.append(line);
                        }
                    }

                    JsonObject state = JsonParser
                            .parseString(body.toString())
                            .getAsJsonObject();

                    extractProgressFromState(state);
                    extractTrackInfo(state);

                    spotifyRunning = true;
                    return;

                } catch (Exception ignored) {
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }
    }

    private void extractProgressFromState(JsonObject state) {
        if (state.has("position") && state.has("duration")) {
            int pos = state.get("position").getAsInt();
            int dur = state.get("duration").getAsInt();

            if (dur > 0) {
                positionMs = pos;
                durationMs = dur;
                hasProgress = true;
                return;
            }
        }

        if (state.has("position_ms") && state.has("duration_ms")) {
            positionMs = state.get("position_ms").getAsInt();
            durationMs = state.get("duration_ms").getAsInt();

            if (durationMs > 0) {
                hasProgress = true;
                return;
            }
        }

        if (state.has("playing_position") && state.has("length")) {
            double pos = state.get("playing_position").getAsDouble();
            durationMs = state.get("length").getAsInt();

            if (durationMs > 0) {
                positionMs = (int) (pos * 1000);
                hasProgress = true;
                return;
            }
        }

        if (state.has("track")) {
            try {
                JsonObject track = state.getAsJsonObject("track");

                if (track.has("length")) {
                    int dur = track.get("length").getAsInt();

                    if (dur > 0) {
                        durationMs = dur;
                        hasProgress = true;

                        if (state.has("playing_position")) {
                            positionMs = (int) (
                                    state.get("playing_position")
                                            .getAsDouble() * 1000
                            );
                        }

                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (state.has("item")) {
            try {
                JsonObject item = state.getAsJsonObject("item");

                if (item.has("duration_ms")) {
                    int dur = item.get("duration_ms").getAsInt();

                    if (dur > 0) {
                        durationMs = dur;
                        hasProgress = true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean extractTrackInfo(JsonObject state) {
        if (state.has("track")) {
            try {
                JsonObject track = state.getAsJsonObject("track");

                if (track.has("track_resource")) {
                    String name = track
                            .getAsJsonObject("track_resource")
                            .get("name")
                            .getAsString();

                    if (name != null && !name.isEmpty()) {
                        currentTitle = name;
                    }
                }

                if (track.has("artist_resource")) {
                    String artist = track
                            .getAsJsonObject("artist_resource")
                            .get("name")
                            .getAsString();

                    if (artist != null && !artist.isEmpty()) {
                        currentArtist = artist;
                    }
                }

                if (!currentTitle.isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        if (state.has("item")) {
            try {
                JsonObject item = state.getAsJsonObject("item");

                if (item.has("name")) {
                    String name = item.get("name").getAsString();

                    if (name != null && !name.isEmpty()) {
                        currentTitle = name;
                    }
                }

                if (item.has("artists")) {
                    JsonArray artists = item.getAsJsonArray("artists");

                    if (artists.size() > 0) {
                        String artist = artists
                                .get(0)
                                .getAsJsonObject()
                                .get("name")
                                .getAsString();

                        if (artist != null && !artist.isEmpty()) {
                            currentArtist = artist;
                        }
                    }
                }

                if (!currentTitle.isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        if (state.has("name")) {
            try {
                String name = state.get("name").getAsString();

                if (name != null && !name.isEmpty()) {
                    currentTitle = name;
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private void extractWebTrackInfo(JsonObject webState) {
        try {
            JsonObject item = webState.getAsJsonObject("item");

            if (item.has("name")) {
                String name = item.get("name").getAsString();

                if (name != null && !name.isEmpty()) {
                    currentTitle = name;
                }
            }

            if (item.has("artists")) {
                JsonArray artists = item.getAsJsonArray("artists");

                if (artists.size() > 0) {
                    String artist = artists
                            .get(0)
                            .getAsJsonObject()
                            .get("name")
                            .getAsString();

                    if (artist != null && !artist.isEmpty()) {
                        currentArtist = artist;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String formatTime(int ms) {
        int total = Math.max(0, ms) / 1000;

        return (total / 60)
                + ":"
                + ((total % 60) < 10 ? "0" : "")
                + (total % 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int alpha(int color, float fade) {
        int a = (color >>> 24) & 255;

        return (clamp(Math.round(a * fade), 0, 255) << 24)
                | (color & 0xFFFFFF);
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

        String ellipsis = "...";

        if (renderer.getWidth(ellipsis) > maxWidth) {
            return "";
        }

        return renderer.trimToWidth(
                text,
                maxWidth - renderer.getWidth(ellipsis)
        ) + ellipsis;
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

        radius = clamp(
                radius,
                0,
                Math.min(width, height) / 2
        );

        if (radius == 0) {
            context.fill(
                    x,
                    y,
                    x + width,
                    y + height,
                    color
            );
            return;
        }

        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;

            int inset = (int) Math.ceil(
                    radius - Math.sqrt(
                            Math.max(
                                    0.0,
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

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String title = currentTitle;

        if (mc.player == null
                || !spotifyRunning
                || title.isEmpty()) {
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        if (screenWidth < 90 || screenHeight < 50) {
            return;
        }

        int panelWidth = Math.min(
                clamp(width.getValue(), 180, 500),
                screenWidth - 8
        );

        int panelHeight = Math.min(108, screenHeight - 8);

        if (panelHeight < 70) {
            return;
        }

        int x;
        int y;

        switch (position.getValue()) {
            case "TopLeft" -> {
                x = 4;
                y = 4;
            }

            case "TopRight" -> {
                x = screenWidth - panelWidth - 4;
                y = 4;
            }

            case "BottomLeft" -> {
                x = 4;
                y = screenHeight - panelHeight - 4;
            }

            default -> {
                x = screenWidth - panelWidth - 4;
                y = screenHeight - panelHeight - 4;
            }
        }

        float fade = animations.getValue()
                ? Math.min(
                        1f,
                        Math.max(
                                0f,
                                (System.nanoTime() - enabledAt)
                                        / 400_000_000f
                        )
                )
                : 1f;

        int background = (
                clamp(opacity.getValue(), 30, 100)
                        * 255 / 100 << 24
        ) | 0x090C13;

        int line = alpha(0xFF454B60, fade);
        int primary = alpha(0xFFF5F6FA, fade);
        int muted = alpha(0xFFA5ACC2, fade);
        int dim = alpha(0xFF646D84, fade);

        int color = alpha(
                0xFF000000 | (accent.getValue() & 0xFFFFFF),
                fade
        );

        rounded(
                context,
                x,
                y,
                panelWidth,
                panelHeight,
                7,
                line
        );

        rounded(
                context,
                x + 1,
                y + 1,
                panelWidth - 2,
                panelHeight - 2,
                6,
                alpha(background, fade)
        );

        int art = Math.min(64, panelHeight - 36);
        int artX = x + 7;
        int artY = y + 7;

        rounded(
                context,
                artX,
                artY,
                art,
                art,
                4,
                alpha(0xFF252B39, fade)
        );

        int middle = artX + art / 2;
        TextRenderer renderer = mc.textRenderer;

        context.drawText(
                renderer,
                "♪",
                middle - renderer.getWidth("♪") / 2,
                artY + art / 2 - 4,
                muted,
                false
        );

        int textX = artX + art + 9;
        int right = x + panelWidth - 8;
        int available = Math.max(0, right - textX);

        context.drawText(
                renderer,
                fit(renderer, "NOW PLAYING", available),
                textX,
                y + 9,
                dim,
                false
        );

        context.drawText(
                renderer,
                fit(renderer, title, available),
                textX,
                y + 23,
                primary,
                false
        );

        String artist = currentArtist.isEmpty()
                ? "Unknown Artist"
                : currentArtist;

        context.drawText(
                renderer,
                fit(renderer, artist, available),
                textX,
                y + 37,
                muted,
                false
        );

        int pos = positionMs;
        int duration = durationMs;

        if (hasProgress && duration > 0 && available > 40) {
            pos = clamp(pos, 0, duration);

            String elapsed = formatTime(pos);
            String remaining = "-" + formatTime(duration - pos);

            int timeY = y + 52;

            context.drawText(
                    renderer,
                    elapsed,
                    textX,
                    timeY,
                    muted,
                    false
            );

            context.drawText(
                    renderer,
                    remaining,
                    right - renderer.getWidth(remaining),
                    timeY,
                    muted,
                    false
            );

            int barY = y + 65;

            rounded(
                    context,
                    textX,
                    barY,
                    available,
                    3,
                    1,
                    alpha(0xFF303543, fade)
            );

            int progress = (int) (
                    (long) available * pos / duration
            );

            if (progress > 0) {
                rounded(
                        context,
                        textX,
                        barY,
                        progress,
                        3,
                        1,
                        color
                );
            }
        }

        if (footer.getValue()) {
            int base = y + panelHeight - 12;
            int barsX = x + 9;
            long now = System.nanoTime();

            for (int i = 0; i < 3; i++) {
                int barHeight = animations.getValue()
                        ? 3 + (int) (
                                3 * (
                                        1 + Math.sin(
                                                now / 350_000_000.0
                                                        + i * 1.2
                                        )
                                )
                        )
                        : 5 + (i == 1 ? 3 : 0);

                context.fill(
                        barsX + i * 4,
                        base - barHeight,
                        barsX + i * 4 + 2,
                        base,
                        color
                );
            }

            context.drawText(
                    renderer,
                    fit(renderer, "Spotify detected", panelWidth - 30),
                    x + 25,
                    y + panelHeight - 20,
                    muted,
                    false
            );
        }
    }
    }
