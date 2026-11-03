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

/** A visual-only update of the existing WinnerClient SpotifyHUD.
 * Its original Windows sources are retained. This is not a mobile media bridge. */
public class SpotifyHUD extends Module {
    // Keep the exact original setting name and values for existing config files.
    private final Setting.ModeSetting position = addMode("Position", "Overlay position", "BottomRight", "TopLeft", "TopRight", "BottomLeft", "BottomRight");
    private final Setting.IntSetting width = addInt("Width", "Preferred panel width", 300, 180, 500);
    private final Setting.IntSetting opacity = addInt("Opacity", "Background opacity percent", 90, 30, 100);
    private final Setting.ColorSetting accent = addColor("Accent", "Progress and waveform color", 0xFF939DBE, false);
    private final Setting.BoolSetting footer = addBool("Footer", "Show media status", true);
    private final Setting.BoolSetting animations = addBool("Animations", "Animate fade-in and waveform", true);

    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile int positionMs;
    private volatile int durationMs;
    private volatile boolean hasProgress;
    private volatile boolean spotifyRunning;
    private volatile boolean running;
    private Thread pollingThread;
    private long enabledAt;

    public SpotifyHUD() { super("SpotifyHUD", "Now playing overlay", Category.RENDER); }

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
            try { fetchTitle(); fetchProgress(); } catch (Exception ignored) { }
            try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
        }
    }

    // Original Windows window-title source, unchanged in intent and parsing.
    private void fetchTitle() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "powershell.exe", "-NoProfile", "-Command",
                "$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; (Get-Process -Name 'spotify','Spotify' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -First 1).MainWindowTitle"
            });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                p.waitFor();
                if (line == null || line.isBlank()) {
                    currentTitle = ""; currentArtist = ""; spotifyRunning = false; return;
                }
                String t = line.trim();
                if (t.endsWith(" - Spotify")) t = t.substring(0, t.length() - " - Spotify".length());
                int sep = t.indexOf(" - ");
                if (sep > 0 && sep < t.length() - 3) {
                    currentArtist = t.substring(0, sep).trim();
                    currentTitle = t.substring(sep + 3).trim();
                } else { currentTitle = t; currentArtist = ""; }
                spotifyRunning = true;
            }
        } catch (Exception ignored) {
            currentTitle = ""; currentArtist = ""; spotifyRunning = false;
        }
    }

    // Original helper file and local/Web API lookup; no requests on the render thread.
    private void fetchProgress() {
        try {
            Path helperPath = Paths.get(System.getenv("APPDATA"), "Spotify", "spotify-helper.json");
            if (!Files.exists(helperPath)) return;
            JsonObject helper = JsonParser.parseString(Files.readString(helperPath)).getAsJsonObject();
            fetchFromLocalApi(helper.get("port").getAsInt(), helper.get("token").getAsString());
        } catch (Exception ignored) { }
    }

    private void fetchFromLocalApi(int port, String token) throws Exception {
        String[] paths = {"/state.json", "/remote/state.json"};
        for (String path : paths) {
            for (boolean useAuth : new boolean[]{true, false}) {
                HttpURLConnection conn = null;
                try {
                    URI uri = new URI("http://127.0.0.1:" + port + path);
                    conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(1500); conn.setReadTimeout(1500);
                    if (useAuth) {
                        conn.setRequestProperty("Origin", "https://open.spotify.com");
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
                    if (conn.getResponseCode() != 200) continue;
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String l; while ((l = r.readLine()) != null) sb.append(l);
                    }
                    JsonObject state = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    extractProgressFromState(state);
                    extractTrackInfo(state);
                    spotifyRunning = true;
                    return;
                } catch (Exception ignored) { }
                finally { if (conn != null) conn.disconnect(); }
            }
        }
        HttpURLConnection webConn = null;
        try {
            URI webUri = new URI("https://api.spotify.com/v1/me/player/currently-playing");
            webConn = (HttpURLConnection) webUri.toURL().openConnection();
            webConn.setRequestMethod("GET");
            webConn.setRequestProperty("Authorization", "Bearer " + token);
            webConn.setConnectTimeout(1500); webConn.setReadTimeout(1500);
            if (webConn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(webConn.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) sb.append(l);
                }
                JsonObject webState = JsonParser.parseString(sb.toString()).getAsJsonObject();
                if (webState.has("item")) {
                    extractWebTrackInfo(webState);
                    if (webState.has("progress_ms")) {
                        JsonObject item = webState.getAsJsonObject("item");
                        if (item.has("duration_ms")) {
                            positionMs = webState.get("progress_ms").getAsInt();
                            durationMs = item.get("duration_ms").getAsInt();
                            hasProgress = true;
                        }
                    }
                    spotifyRunning = true;
                }
            }
        } catch (Exception ignored) { }
        finally { if (webConn != null) webConn.disconnect(); }
    }

    private void extractProgressFromState(JsonObject state) {
        if (state.has("position") && state.has("duration")) {
            int pos = state.get("position").getAsInt(), dur = state.get("duration").getAsInt();
            if (dur > 0) { positionMs = pos; durationMs = dur; hasProgress = true; return; }
        }
        if (state.has("position_ms") && state.has("duration_ms")) {
            positionMs = state.get("position_ms").getAsInt(); durationMs = state.get("duration_ms").getAsInt();
            if (durationMs > 0) { hasProgress = true; return; }
        }
        if (state.has("playing_position") && state.has("length")) {
            double pos = state.get("playing_position").getAsDouble(); durationMs = state.get("length").getAsInt();
            if (durationMs > 0) { positionMs = (int)(pos * 1000); hasProgress = true; return; }
        }
        if (state.has("track")) {
            try {
                JsonObject track = state.getAsJsonObject("track");
                if (track.has("length")) {
                    int dur = track.get("length").getAsInt();
                    if (dur > 0) {
                        durationMs = dur; hasProgress = true;
                        if (state.has("playing_position")) positionMs = (int)(state.get("playing_position").getAsDouble() * 1000);
                        return;
                    }
                }
            } catch (Exception ignored) { }
        }
        if (state.has("item")) {
            try {
                JsonObject item = state.getAsJsonObject("item");
                if (item.has("duration_ms")) {
                    int dur = item.get("duration_ms").getAsInt();
                    if (dur > 0) { durationMs = dur; hasProgress = true; }
                }
            } catch (Exception ignored) { }
        }
    }

    private boolean extractTrackInfo(JsonObject state) {
        if (state.has("track")) {
            try {
                JsonObject track = state.getAsJsonObject("track");
                if (track.has("track_resource")) {
                    String name = track.getAsJsonObject("track_resource").get("name").getAsString();
                    if (name != null && !name.isEmpty()) currentTitle = name;
                }
                if (track.has("artist_resource")) {
                    String artist = track.getAsJsonObject("artist_resource").get("name").getAsString();
                    if (artist != null && !artist.isEmpty()) currentArtist = artist;
                }
                if (!currentTitle.isEmpty()) return true;
            } catch (Exception ignored) { }
        }
        if (state.has("item")) {
            try {
                JsonObject item = state.getAsJsonObject("item");
                if (item.has("name")) {
                    String name = item.get("name").getAsString();
                    if (name != null && !name.isEmpty()) currentTitle = name;
                }
                if (item.has("artists")) {
                    JsonArray artists = item.getAsJsonArray("artists");
                    if (artists.size() > 0) {
                        String artist = artists.get(0).getAsJsonObject().get("name").getAsString();
                        if (artist != null && !artist.isEmpty()) currentArtist = artist;
                    }
                }
                if (!currentTitle.isEmpty()) return true;
            } catch (Exception ignored) { }
        }
        if (state.has("name")) {
            try {
                String name = state.get("name").getAsString();
                if (name != null && !name.isEmpty()) { currentTitle = name; return true; }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void extractWebTrackInfo(JsonObject webState) {
        try {
            JsonObject item = webState.getAsJsonObject("item");
            if (item.has("name")) {
                String name = item.get("name").getAsString();
                if (name != null && !name.isEmpty()) currentTitle = name;
            }
            if (item.has("artists")) {
                JsonArray artists = item.getAsJsonArray("artists");
                if (artists.size() > 0) {
                    String artist = artists.get(0).getAsJsonObject().get("name").getAsString();
                    if (artist != null && !artist.isEmpty()) currentArtist = artist;
                }
            }
        } catch (Exception ignored) { }
    }

    private static String formatTime(int ms) {
        int total = Math.max(0, ms) / 1000;
        return (total / 60) + ":" + ((total % 60) < 10 ? "0" : "") + (total % 60);
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int alpha(int color, float fade) {
        int a = (color >>> 24) & 255;
        return (clamp(Math.round(a * fade), 0, 255) << 24) | (color & 0xFFFFFF);
    }
    private static String fit(TextRenderer tr, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (tr.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        if (tr.getWidth(ellipsis) > maxWidth) return "";
        return tr.trimToWidth(text, maxWidth - tr.getWidth(ellipsis)) + ellipsis;
    }
    // Small integer-radius corners: no textures, shaders or GL state changes.
    private static void rounded(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = clamp(r, 0, Math.min(w, h) / 2);
        if (r == 0) { c.fill(x, y, x + w, y + h, color); return; }
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int)Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            c.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            c.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String title = currentTitle;
        if (mc.player == null || !spotifyRunning || title.isEmpty()) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        if (sw < 90 || sh < 50) return;
        int w = Math.min(clamp(width.getValue(), 180, 500), sw - 8);
        int h = Math.min(108, sh - 8);
        if (h < 70) return;
        int x, y;
        switch (position.getValue()) {
            case "TopLeft" -> { x = 4; y = 4; }
            case "TopRight" -> { x = sw - w - 4; y = 4; }
            case "BottomLeft" -> { x = 4; y = sh - h - 4; }
            default -> { x = sw - w - 4; y = sh - h - 4; }
        }
        float fade = animations.getValue() ? Math.min(1f, Math.max(0f, (System.nanoTime() - enabledAt) / 400_000_000f)) : 1f;
        int bg = (clamp(opacity.getValue(), 30, 100) * 255 / 100 << 24) | 0x090C13;
        int line = alpha(0xFF454B60, fade);
        int primary = alpha(0xFFF5F6FA, fade);
        int muted = alpha(0xFFA5ACC2, fade);
        int dim = alpha(0xFF646D84, fade);
        int color = alpha(0xFF000000 | (accent.getValue() & 0xFFFFFF), fade);
        rounded(context, x, y, w, h, 7, line);
        rounded(context, x + 1, y + 1, w - 2, h - 2, 6, alpha(bg, fade));
        // Placeholder is deliberately not represented as actual album artwork.
        int art = Math.min(64, h - 36);
        int ax = x + 7, ay = y + 7;
        rounded(context, ax, ay, art, art, 4, alpha(0xFF252B39, fade));
        int mid = ax + art / 2;
        context.drawText(mc.textRenderer, "♪", mid - mc.textRenderer.getWidth("♪") / 2, ay + art / 2 - 4, muted, false);
        int tx = ax + art + 9, right = x + w - 8, available = Math.max(0, right - tx);
        TextRenderer tr = mc.textRenderer;
        context.drawText(tr, fit(tr, "NOW PLAYING", available), tx, y + 9, dim, false);
        context.drawText(tr, fit(tr, title, available), tx, y + 23, primary, false);
        String artist = currentArtist.isEmpty() ? "Unknown Artist" : currentArtist;
        context.drawText(tr, fit(tr, artist, available), tx, y + 37, muted, false);
        int pos = positionMs, duration = durationMs;
        if (hasProgress && duration > 0 && available > 40) {
            pos = clamp(pos, 0, duration);
            String elapsed = formatTime(pos), remaining = "-" + formatTime(duration - pos);
            int timeY = y + 52;
            context.drawText(tr, elapsed, tx, timeY, muted, false);
            context.drawText(tr, remaining, right - tr.getWidth(remaining), timeY, muted, false);
            int barY = y + 65;
            rounded(context, tx, barY, available, 3, 1, alpha(0xFF303543, fade));
            int progress = (int)((long)available * pos / duration);
            if (progress > 0) rounded(context, tx, barY, progress, 3, 1, color);
        }
        if (footer.getValue()) {
            int base = y + h - 12, bx = x + 9;
            long now = System.nanoTime();
            for (int i = 0; i < 3; i++) {
                int bh = animations.getValue() ? 3 + (int)(3 * (1 + Math.sin(now / 350_000_000.0 + i * 1.2))) : 5 + (i == 1 ? 3 : 0);
                context.fill(bx + i * 4, base - bh, bx + i * 4 + 2, base, color);
            }
            context.drawText(tr, fit(tr, "Spotify detected", w - 30), x + 25, y + h - 20, muted, false);
        }
    }
}
