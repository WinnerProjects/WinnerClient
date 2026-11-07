package com.winnerclient.fabric.media;

import com.winnerclient.fabric.media.bridge.BridgeAuth;
import com.winnerclient.fabric.media.bridge.FlatJson;
import com.winnerclient.fabric.media.bridge.Frames;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * MediaProvider backed by the SpotifyBridge Android app (127.0.0.1 only).
 *
 * Threading: every blocking operation (connect, read, write, decode of the base64 art payload)
 * runs on this provider's own daemon threads. {@link #snapshot()} is a volatile read and
 * {@link #command} only enqueues work, so neither can block the render/game thread.
 *
 * The pairing key is read through the supplier on every connection attempt, is never logged,
 * and is never sent over the socket (mutual HMAC challenge-response, see {@link BridgeAuth}).
 * Nothing about media is invented: if the bridge is offline the snapshot is empty with a
 * status string explaining why.
 */
public final class AndroidMediaProvider implements MediaProvider {
    public static final int DEFAULT_PORT = 29821;

    private static final System.Logger LOG = System.getLogger("WinnerClient/AndroidBridge");

    private static final int CONNECT_TIMEOUT_MS = 1_500;
    private static final int HANDSHAKE_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 12_000;       // server pings every 4 s
    private static final int MAX_HANDSHAKE_FRAME = 512;
    private static final int MAX_INBOUND_FRAME = 1_000_000;  // art PNG is capped at 700 KB decoded
    private static final int MAX_ART_BYTES = 700_000;
    private static final int MAX_TEXT = 512;
    private static final long[] BACKOFF_MS = {1_000, 2_000, 4_000, 8_000, 15_000};

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    private final Supplier<String> keySupplier;
    private final int port;

    private volatile MediaSnapshot snapshot = MediaSnapshot.empty("Starting Android bridge");
    private volatile boolean closed;
    private volatile Session session;

    private final Thread worker;
    private final ExecutorService commandSender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WinnerClient-AndroidBridge-send");
        t.setDaemon(true);
        return t;
    });

    // Worker-thread-only art cache: the same byte[] instance is reused across snapshots so the
    // HUD does not re-decode when only the position changes.
    private byte[] artBytes;
    private String artId;
    private boolean authenticated; // worker thread only

    public AndroidMediaProvider(Supplier<String> keySupplier) {
        this(keySupplier, DEFAULT_PORT);
    }

    public AndroidMediaProvider(Supplier<String> keySupplier, int port) {
        this.keySupplier = keySupplier;
        this.port = port;
        this.worker = new Thread(this::run, "WinnerClient-AndroidBridge");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public MediaSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void command(Command command, long positionMs) {
        Session s = session;
        if (closed || s == null) {
            return;
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("t", "cmd");
        msg.put("c", command.name());
        msg.put("pos", Math.max(0L, positionMs));
        byte[] payload = Frames.utf8(FlatJson.write(msg));

        try {
            commandSender.execute(() -> s.send(payload));
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    public void close() {
        closed = true;
        Session s = session;
        if (s != null) {
            s.close();
        }
        worker.interrupt();
        commandSender.shutdownNow();
        snapshot = MediaSnapshot.empty("Stopped");
    }

    // ------------------------------------------------------------------ connection loop

    private void run() {
        int attempt = 0;
        String lastTyped = null;

        while (!closed) {
            String typed = readKey();

            // A different key deserves a fresh start of the retry schedule.
            if (!Objects.equals(typed, lastTyped)) {
                attempt = 0;
                lastTyped = typed;
            }

            if (typed == null || typed.isBlank()) {
                offline("Bridge key missing");
                sleepUnlessKeyChanged(2_000, typed);
                continue;
            }

            byte[] key = BridgeAuth.parseKey(typed);

            if (key == null) {
                offline("Bridge key invalid");
                sleepUnlessKeyChanged(2_000, typed);
                continue;
            }

            long delay;
            authenticated = false;

            try {
                connectOnce(key);
                offline("Android bridge offline");
            } catch (PairingException e) {
                offline("Pairing failed - check key");
                LOG.log(System.Logger.Level.WARNING, "Android bridge pairing failed");
                delay = BACKOFF_MS[Math.min(attempt++, BACKOFF_MS.length - 1)];
                sleepUnlessKeyChanged(delay, typed);
                continue;
            } catch (IOException | RuntimeException e) {
                if (closed) {
                    return;
                }
                offline("Android bridge offline");
            }

            // A session that authenticated and later dropped should reconnect quickly.
            if (authenticated) {
                attempt = 0;
            }

            delay = BACKOFF_MS[Math.min(attempt++, BACKOFF_MS.length - 1)];
            sleepUnlessKeyChanged(delay, typed);
        }
    }

    private void connectOnce(byte[] key) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(
                            InetAddress.getByAddress(new byte[]{127, 0, 0, 1}),
                            port
                    ),
                    CONNECT_TIMEOUT_MS
            );
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // 1. server hello
            Map<String, Object> hello = readMessage(in, MAX_HANDSHAKE_FRAME);
            if (!"hello".equals(str(hello, "t"))
                    || !Long.valueOf(1L).equals(hello.get("v"))) {
                throw new IOException("unexpected hello");
            }
            byte[] ns = BridgeAuth.fromHex(str(hello, "ns"), BridgeAuth.NONCE_BYTES);
            if (ns == null) {
                throw new IOException("bad server nonce");
            }

            // 2. our proof (the key itself is never transmitted)
            byte[] nc = BridgeAuth.randomBytes(BridgeAuth.NONCE_BYTES);
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("t", "auth");
            auth.put("nc", BridgeAuth.toHex(nc));
            auth.put("p", BridgeAuth.toHex(
                    BridgeAuth.proof(key, BridgeAuth.LABEL_CLIENT, ns, nc)
            ));
            Frames.write(out, Frames.utf8(FlatJson.write(auth)));

            // 3. verify the SERVER's proof - this is the "mutual" half
            Map<String, Object> reply = readMessage(in, MAX_HANDSHAKE_FRAME);
            String type = str(reply, "t");
            if ("fail".equals(type)) {
                throw new PairingException();
            }
            byte[] serverProof = BridgeAuth.fromHex(str(reply, "p"), 32);
            if (!"ok".equals(type)
                    || serverProof == null
                    || !BridgeAuth.constantTimeEquals(
                            serverProof,
                            BridgeAuth.proof(key, BridgeAuth.LABEL_SERVER, nc, ns))) {
                // Peer could not prove it knows the key: do not trust anything it sends.
                throw new PairingException();
            }

            authenticated = true;
            Session s = new Session(socket, out);
            session = s;
            socket.setSoTimeout(READ_TIMEOUT_MS);
            LOG.log(System.Logger.Level.INFO, "Android bridge connected");

            try {
                while (!closed) {
                    Map<String, Object> msg = readMessage(in, MAX_INBOUND_FRAME);
                    String t = str(msg, "t");

                    if ("state".equals(t)) {
                        apply(msg);
                    } else if ("ping".equals(t)) {
                        Map<String, Object> pong = new LinkedHashMap<>();
                        pong.put("t", "pong");
                        s.send(Frames.utf8(FlatJson.write(pong)));
                    }
                }
            } finally {
                session = null;
                s.close();
                clearArt();
                LOG.log(System.Logger.Level.INFO, "Android bridge disconnected");
            }
        }
    }

    // ------------------------------------------------------------------ state handling

    private void apply(Map<String, Object> msg) {
        if (!Boolean.TRUE.equals(msg.get("ok"))) {
            clearArt();
            snapshot = MediaSnapshot.empty(text(msg, "st", "No media session"));
            return;
        }

        String aid = str(msg, "aid");
        String art = str(msg, "art");

        if (art != null && aid != null) {
            byte[] decoded = decodeArt(art);
            if (decoded != null) {
                artBytes = decoded;
                artId = aid;
            }
        }

        if (aid == null || !aid.equals(artId)) {
            // No cover announced, or announced but not delivered: show none rather than a stale one.
            artBytes = null;
            artId = null;
        }

        String title = cap(text(msg, "title", ""));
        String artist = cap(text(msg, "artist", ""));
        String album = cap(text(msg, "album", ""));
        String id = text(msg, "id", title + "\u0000" + artist + "\u0000" + album);

        long duration = number(msg, "dur", -1);
        long position = number(msg, "pos", -1);

        if (duration <= 0) {
            duration = -1;
        }

        snapshot = new MediaSnapshot(
                id + "|" + (artBytes != null ? artId : "-"),
                title,
                artist,
                album,
                artBytes,
                Boolean.TRUE.equals(msg.get("play")),
                position < 0 ? -1 : position,
                duration,
                System.nanoTime(),
                Boolean.TRUE.equals(msg.get("prev")),
                Boolean.TRUE.equals(msg.get("tog")),
                Boolean.TRUE.equals(msg.get("next")),
                Boolean.TRUE.equals(msg.get("seek")) && duration > 0,
                text(msg, "st", "Spotify (Android)")
        );
    }

    private static byte[] decodeArt(String base64) {
        // Cheap upper bound before allocating: 4 chars -> 3 bytes.
        if (base64.length() / 4 * 3 > MAX_ART_BYTES + 3) {
            return null;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (bytes.length < 8 || bytes.length > MAX_ART_BYTES) {
            return null;
        }

        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return null;
            }
        }

        return bytes;
    }

    private void clearArt() {
        artBytes = null;
        artId = null;
    }

    private void offline(String status) {
        if (!closed) {
            snapshot = MediaSnapshot.empty(status);
        }
    }

    // ------------------------------------------------------------------ small helpers

    private String readKey() {
        try {
            return keySupplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void sleepUnlessKeyChanged(long millis, String typedKey) {
        long end = System.nanoTime() + millis * 1_000_000L;

        while (!closed && System.nanoTime() < end) {
            if (!Objects.equals(readKey(), typedKey)) {
                return;
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static Map<String, Object> readMessage(InputStream in, int max) throws IOException {
        byte[] raw = Frames.read(in, max);

        if (raw == null) {
            throw new IOException("connection closed");
        }

        try {
            return FlatJson.parse(Frames.decodeUtf8(raw));
        } catch (IllegalArgumentException e) {
            throw new IOException("malformed message");
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : null;
    }

    private static String text(Map<String, Object> m, String key, String fallback) {
        String v = str(m, key);
        return v == null ? fallback : v;
    }

    private static long number(Map<String, Object> m, String key, long fallback) {
        Object v = m.get(key);

        if (v instanceof Long l) {
            return l;
        }

        if (v instanceof Double d && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
            return (long) d.doubleValue();
        }

        return fallback;
    }

    private static String cap(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    /** Wrong key, or a peer that could not prove it knows it. */
    private static final class PairingException extends IOException {
        private static final long serialVersionUID = 1L;

        PairingException() {
            super("pairing failed");
        }
    }

    private static final class Session {
        private final Socket socket;
        private final OutputStream out;

        Session(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        void send(byte[] payload) {
            try {
                synchronized (out) {
                    Frames.write(out, payload);
                }
            } catch (IOException e) {
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
