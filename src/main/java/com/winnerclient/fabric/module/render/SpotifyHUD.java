package com.winnerclient.fabric.module.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winnerclient.fabric.module.Module;
import com.winnerclient.fabric.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import com.google.gson.JsonArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpotifyHUD extends Module {
    private final Setting.ModeSetting position = addMode("Position", "Overlay position", "BottomRight", "TopLeft", "TopRight", "BottomLeft", "BottomRight");

    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile int positionMs;
    private volatile int durationMs;
    private volatile boolean hasProgress;
    private volatile boolean spotifyRunning;
    private Thread pollingThread;
    private volatile boolean running;

    private static final int GREEN = 0xFF1DB954;
    private static final int BG = 0xDD141414;
    private static final int BORDER = 0xFF222222;
    private static final int PH = 60;
    private static final int GAP = 4;

    public SpotifyHUD() {
        super("SpotifyHUD", "Now playing overlay", Category.RENDER);
    }

    @Override
    public void onEnable() {
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
        while (running) {
            try {
                fetchTitle();
                fetchProgress();
            } catch (Exception ignored) {}
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
                "powershell.exe", "-NoProfile", "-Command",
                "$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; (Get-Process -Name 'spotify','Spotify' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -First 1).MainWindowTitle"
            });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                p.waitFor();
                if (line == null || line.isBlank()) {
                    currentTitle = "";
                    currentArtist = "";
                    spotifyRunning = false;
                    return;
                }
                String t = line.trim();
                if (t.endsWith(" - Spotify")) t = t.substring(0, t.length() - " - Spotify".length());
                int sep = t.indexOf(" - ");
                if (sep > 0 && sep < t.length() - 3) {
                    currentArtist = t.substring(0, sep).trim();
                    currentTitle = t.substring(sep + 3).trim();
                } else {
                    currentTitle = t;
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

    private void fetchProgress() {
        try {
            Path helperPath = Paths.get(System.getenv("APPDATA"), "Spotify", "spotify-helper.json");
            if (!Files.exists(helperPath)) return;
            String content = Files.readString(helperPath);
            JsonObject helper = JsonParser.parseString(content).getAsJsonObject();
            int port = helper.get("port").getAsInt();
            String token = helper.get("token").getAsString();

            fetchFromLocalApi(port, token);
        } catch (Exception ignored) {}
    }

    private void fetchFromLocalApi(int port, String token) throws Exception {
        String[] paths = {"/state.json", "/remote/state.json"};
        for (String path : paths) {
            for (boolean useAuth : new boolean[]{true, false}) {
            try {
                URI uri = new URI("http://127.0.0.1:" + port + path);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                if (useAuth) {
                    conn.setRequestProperty("Origin", "https://open.spotify.com");
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) continue;
                String body;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    body = sb.toString();
                }
                JsonObject state = JsonParser.parseString(body).getAsJsonObject();

                extractProgressFromState(state);
                extractTrackInfo(state);
                spotifyRunning = true;
                return;
            } catch (Exception ignored) {}
            }
        }

        try {
            URI webUri = new URI("https://api.spotify.com/v1/me/player/currently-playing");
            HttpURLConnection webConn = (HttpURLConnection) webUri.toURL().openConnection();
            webConn.setRequestMethod("GET");
            webConn.setRequestProperty("Authorization", "Bearer " + token);
            webConn.setConnectTimeout(1500);
            webConn.setReadTimeout(1500);

            int webCode = webConn.getResponseCode();
            if (webCode == 200) {
                String webBody;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(webConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    webBody = sb.toString();
                }
                JsonObject webState = JsonParser.parseString(webBody).getAsJsonObject();
                if (webState.has("item")) {
                    extractWebTrackInfo(webState);
                    if (webState.has("progress_ms") && webState.has("item")) {
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
        } catch (Exception ignored) {}
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
            if (durationMs > 0) { hasProgress = true; return; }
        }
        if (state.has("playing_position") && state.has("length")) {
            double pos = state.get("playing_position").getAsDouble();
            durationMs = state.get("length").getAsInt();
            if (durationMs > 0) {
                positionMs = (int)(pos * 1000);
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
                            positionMs = (int)(state.get("playing_position").getAsDouble() * 1000);
                        }
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (state.has("item")) {
            try {
                JsonObject item = state.getAsJsonObject("item");
                if (item.has("duration_ms")) {
                    int dur = item.get("duration_ms").getAsInt();
                    if (dur > 0) {
                        durationMs = dur;
                        hasProgress = true;
                        return;
                    }
                }
            } catch (Exception ignored) {}
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
                if (currentTitle != null && !currentTitle.isEmpty()) return true;
            } catch (Exception ignored) {}
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
                if (currentTitle != null && !currentTitle.isEmpty()) return true;
            } catch (Exception ignored) {}
        }

        if (state.has("name")) {
            try {
                String name = state.get("name").getAsString();
                if (name != null && !name.isEmpty()) {
                    currentTitle = name;
                    return true;
                }
            } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    @Override
    public void onRender(DrawContext context, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !spotifyRunning || currentTitle.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;

        if (hasProgress && durationMs > 0 && positionMs <= durationMs) {
            long tick = System.currentTimeMillis();
            long elapsed = (tick % 2000);
            if (elapsed < 500) positionMs += 2;
        }

        String titleStr = currentTitle;
        String artistStr = currentArtist.isEmpty() ? "Unknown Artist" : currentArtist;
        int titleW = tr.getWidth(titleStr);
        int artistW = tr.getWidth(artistStr);

        int pw = MathHelper.clamp(Math.max(titleW, artistW) + 28, 174, 320);

        int sW = mc.getWindow().getScaledWidth();
        int sH = mc.getWindow().getScaledHeight();

        int x, y;
        switch (position.getValue()) {
            case "TopLeft"      -> { x = GAP; y = GAP; }
            case "TopRight"     -> { x = sW - pw - GAP; y = GAP; }
            case "BottomLeft"   -> { x = GAP; y = sH - PH - GAP; }
            default             -> { x = sW - pw - GAP; y = sH - PH - GAP; }
        }

        context.fill(x - 1, y - 1, x + pw + 1, y + PH + 1, BORDER);
        context.fill(x, y, x + pw, y + PH, BG);

        context.fill(x + 8, y + 8, x + 12, y + PH - 8, GREEN);
        context.fill(x + 12, y + 8, x + 13, y + PH - 8, 0xFF18A349);

        int tx = x + 18;
        int ty = y + 10;

        context.drawText(tr, titleStr, tx, ty, 0xFFFFFFFF, false);
        context.drawText(tr, artistStr, tx, ty + 12, 0xFFAAAAAA, false);

        int eqY = y + PH - 14;
        int cx = tx;

        long t = System.currentTimeMillis();
        int[] hs = {
            (int)(Math.sin(t * 0.003) * 3 + 5),
            (int)(Math.sin(t * 0.005 + 1) * 4 + 5),
            (int)(Math.sin(t * 0.004 + 2) * 3 + 5),
            (int)(Math.sin(t * 0.006 + 0.5) * 4 + 5),
            (int)(Math.sin(t * 0.0035 + 3) * 3 + 5),
            (int)(Math.sin(t * 0.0055 + 1.5) * 4 + 5),
        };

        for (int i = 0; i < 6; i++) {
            context.fill(cx + i * 5, eqY - hs[i], cx + i * 5 + 3, eqY, 0xFFFFFFFF);
        }
    }
}
