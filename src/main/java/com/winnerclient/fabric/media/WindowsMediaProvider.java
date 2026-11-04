package com.winnerclient.fabric.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class WindowsMediaProvider implements MediaProvider {
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "WinnerClient-WindowsMedia");
                thread.setDaemon(true);
                return thread;
            });

    private final Supplier<String> source;

    private volatile MediaSnapshot snapshot =
            MediaSnapshot.empty("Starting Windows media");

    private volatile Process process;
    private volatile boolean closed;

    private Path script;

    public WindowsMediaProvider(Supplier<String> source) {
        this.source = source;

        worker.scheduleWithFixedDelay(
                this::poll,
                0,
                1500,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public MediaSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void command(Command command, long positionMs) {
        if (closed) {
            return;
        }

        try {
            worker.execute(() -> {
                if (closed) {
                    return;
                }

                try {
                    invoke(command.name(), "", positionMs);
                } catch (Exception ignored) {
                }

                poll();
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void poll() {
        if (closed) {
            return;
        }

        try {
            MediaSnapshot old = snapshot;
            JsonObject json = invoke("poll", old.key(), 0);

            if (!json.has("available")
                    || !json.get("available").getAsBoolean()) {

                if (!closed) {
                    snapshot = MediaSnapshot.empty(
                            text(json, "status", "No media session")
                    );
                }

                return;
            }

            String key = text(json, "key", "");

            byte[] artwork = key.equals(old.key())
                    ? old.artwork()
                    : null;

            if (json.has("art") && !json.get("art").isJsonNull()) {
                byte[] decoded = Base64.getDecoder()
                        .decode(json.get("art").getAsString());

                if (decoded.length <= 2_097_152) {
                    artwork = decoded;
                }
            }

            MediaSnapshot updated = new MediaSnapshot(
                    key,
                    text(json, "title", ""),
                    text(json, "artist", ""),
                    text(json, "album", ""),
                    artwork,
                    bool(json, "playing"),
                    number(json, "position"),
                    number(json, "duration"),
                    System.nanoTime(),
                    bool(json, "previous"),
                    bool(json, "toggle"),
                    bool(json, "next"),
                    bool(json, "seek"),
                    text(json, "status", "System media")
            );

            if (!closed) {
                snapshot = updated;
            }
        } catch (Exception e) {
            if (!closed) {
                snapshot = MediaSnapshot.empty(
                        "Windows media unavailable"
                );
            }
        }
    }

    private JsonObject invoke(
            String action,
            String key,
            long position
    ) throws Exception {
        if (script == null) {
            script = Files.createTempFile(
                    "winnerclient-media-",
                    ".ps1"
            );

            try (InputStream input =
                         WindowsMediaProvider.class.getResourceAsStream(
                                 "/assets/winnerclient/media/windows-media.ps1"
                         )) {

                if (input == null) {
                    throw new IllegalStateException(
                            "Windows media script missing"
                    );
                }

                Files.copy(
                        input,
                        script,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        }

        Path output = Files.createTempFile(
                "winnerclient-media-result-",
                ".json"
        );

        try {
            List<String> args = new ArrayList<>(List.of(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.toString(),
                    "-Action",
                    action,
                    "-Source",
                    source.get(),
                    "-KnownKey",
                    key.isEmpty() ? "-" : key,
                    "-PositionMs",
                    Long.toString(position)
            ));

            Process started = new ProcessBuilder(args)
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            process = started;

            if (!started.waitFor(8, TimeUnit.SECONDS)) {
                started.destroyForcibly();
                throw new IllegalStateException("WinRT timeout");
            }

            if (started.exitValue() != 0
                    || Files.size(output) > 3_000_000) {
                throw new IllegalStateException("WinRT failed");
            }

            return JsonParser.parseString(
                    Files.readString(output, StandardCharsets.UTF_8)
            ).getAsJsonObject();

        } finally {
            process = null;
            Files.deleteIfExists(output);
        }
    }

    private static String text(
            JsonObject json,
            String name,
            String fallback
    ) {
        return json.has(name) && !json.get(name).isJsonNull()
                ? json.get(name).getAsString()
                : fallback;
    }

    private static boolean bool(JsonObject json, String name) {
        return json.has(name)
                && !json.get(name).isJsonNull()
                && json.get(name).getAsBoolean();
    }

    private static long number(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull()
                ? json.get(name).getAsLong()
                : -1;
    }

    @Override
    public void close() {
        closed = true;

        Process current = process;
        if (current != null) {
            current.destroyForcibly();
        }

        worker.shutdownNow();

        if (script != null) {
            try {
                Files.deleteIfExists(script);
            } catch (Exception ignored) {
                script.toFile().deleteOnExit();
            }
        }

        snapshot = MediaSnapshot.empty("Stopped");
    }
          }
