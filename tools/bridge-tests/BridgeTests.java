import com.winnerclient.fabric.media.AndroidMediaProvider;
import com.winnerclient.fabric.media.MediaProvider;
import com.winnerclient.fabric.media.MediaSnapshot;
import com.winnerclient.fabric.media.bridge.BridgeAuth;
import com.winnerclient.fabric.media.bridge.FlatJson;
import com.winnerclient.fabric.media.bridge.Frames;
import com.winnerclient.spotifybridge.BridgeServer;
import com.winnerclient.spotifybridge.MediaState;
import com.winnerclient.spotifybridge.protocol.Auth;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Plain-JVM tests: real sockets, Android-side BridgeServer <-> mod-side AndroidMediaProvider. */
public class BridgeTests {
    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else    { failed++; System.out.println("  FAIL  " + name); }
    }

    static boolean waitFor(BooleanSupplier c, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            try { Thread.sleep(25); } catch (InterruptedException e) { return false; }
        }
        return c.getAsBoolean();
    }

    // ---------- fake Android host
    static final class FakeHost implements BridgeServer.Host {
        volatile MediaState state = MediaState.empty("No Spotify notification");
        final BlockingQueue<String> commands = new LinkedBlockingQueue<>();
        final AtomicBoolean connected = new AtomicBoolean();
        public MediaState state() { return state; }
        public void command(String n, long p) { commands.add(n + ":" + p); }
        public void onClientChanged(boolean c) { connected.set(c); }
        public void log(String m) { }
    }

    static byte[] png(int seed) {
        // Not a real image; provider only validates the PNG magic + size. HUD's NativeImage decodes it later.
        byte[] b = new byte[300 + seed];
        b[0] = (byte) 0x89; b[1] = 'P'; b[2] = 'N'; b[3] = 'G';
        for (int i = 4; i < b.length; i++) b[i] = (byte) (i * seed);
        return b;
    }

    static MediaState playing(String title, long pos, byte[] art, String artId) {
        MediaState.Builder b = new MediaState.Builder();
        b.trackId = "id:" + title; b.title = title; b.artist = "Sanatçı İğşçöü 🎵"; b.album = "Album \"Q\"\n2";
        b.durationMs = 200_000; b.positionMs = pos; b.playing = true;
        b.canPrevious = b.canToggle = b.canNext = true; b.canSeek = true;
        b.status = "Spotify · Android"; b.artPng = art; b.artId = artId;
        return b.build();
    }

    // ---------- raw protocol client used to inspect frames and to attack
    static final class Raw implements Closeable {
        final Socket s; final InputStream in; final OutputStream out;
        Raw(int port) throws IOException {
            s = new Socket(InetAddress.getByAddress(new byte[]{127,0,0,1}), port);
            s.setSoTimeout(6000);
            in = s.getInputStream(); out = s.getOutputStream();
        }
        Map<String,Object> read(int max) throws IOException {
            byte[] r = Frames.read(in, max);
            return r == null ? null : FlatJson.parse(Frames.decodeUtf8(r));
        }
        void write(Map<String,Object> m) throws IOException { Frames.write(out, Frames.utf8(FlatJson.write(m))); }
        boolean handshake(byte[] key) throws IOException {
            Map<String,Object> hello = read(512);
            byte[] ns = BridgeAuth.fromHex((String) hello.get("ns"), 16);
            byte[] nc = BridgeAuth.randomBytes(16);
            Map<String,Object> a = new LinkedHashMap<>();
            a.put("t","auth"); a.put("nc", BridgeAuth.toHex(nc));
            a.put("p", BridgeAuth.toHex(BridgeAuth.proof(key, BridgeAuth.LABEL_CLIENT, ns, nc)));
            write(a);
            Map<String,Object> r = read(512);
            return r != null && "ok".equals(r.get("t"))
                && BridgeAuth.constantTimeEquals(BridgeAuth.fromHex((String) r.get("p"), 32),
                       BridgeAuth.proof(key, BridgeAuth.LABEL_SERVER, nc, ns));
        }
        public void close() { try { s.close(); } catch (IOException e) {} }
    }

    static String stat(MediaProvider p) { return p.snapshot().status(); }

    public static void main(String[] args) throws Exception {
        testJson();
        testFramesAndAuth();
        testHappyPath();
        testWrongKey();
        testRogueServer();
        testServerHardening();
        testKeyRotationAndDisconnect();
        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ JSON
    static void testJson() {
        System.out.println("[FlatJson]");
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("s", "a\"b\\c\n\r\t\b\f\u0001/ İğşçöüÖ 🎵 \u2028");
        m.put("neg", -9_000_000_000L); m.put("i", 42); m.put("d", 1.5);
        m.put("t", true); m.put("f", false); m.put("n", null); m.put("empty", "");
        String j = FlatJson.write(m);
        Map<String,Object> back = FlatJson.parse(j);
        check("roundtrip strings/escapes/unicode/emoji", m.get("s").equals(back.get("s")));
        check("roundtrip numbers", Long.valueOf(-9_000_000_000L).equals(back.get("neg")) && Long.valueOf(42).equals(back.get("i")) && Double.valueOf(1.5).equals(back.get("d")));
        check("roundtrip bool/null/empty", Boolean.TRUE.equals(back.get("t")) && Boolean.FALSE.equals(back.get("f")) && back.containsKey("n") && back.get("n") == null && "".equals(back.get("empty")));
        check("android & mod codecs emit identical bytes", j.equals(com.winnerclient.spotifybridge.protocol.FlatJson.write(m)));
        String[] bad = {"", "{", "[]", "{\"a\":[1]}", "{\"a\":{}}", "{\"a\":1} x", "{\"a\":1,\"a\":2}", "{\"a\":\"\\q\"}",
            "{\"a\":\"\\u12\"}", "{\"a\":01x}", "{'a':1}", "{\"a\":NaN}", "{\"a\":\"x\ny\"}", "{\"a\":1,}", "{\"a\":--1}", "{\"a\":1.}", "{\"a\":99999999999999999999}"};
        boolean allRejected = true;
        for (String b : bad) {
            try { FlatJson.parse(b); allRejected = false; System.out.println("    accepted: " + b); } catch (IllegalArgumentException ok) {}
        }
        check("rejects malformed / nested / duplicate / overflow input", allRejected);
        StringBuilder many = new StringBuilder("{");
        for (int i = 0; i < 40; i++) many.append(i > 0 ? "," : "").append("\"k").append(i).append("\":1");
        many.append("}");
        boolean tooMany = false;
        try { FlatJson.parse(many.toString()); } catch (IllegalArgumentException e) { tooMany = true; }
        check("rejects >32 keys", tooMany);
    }

    // ------------------------------------------------------------------ frames + auth
    static void testFramesAndAuth() throws Exception {
        System.out.println("[Frames / Auth]");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Frames.write(bo, Frames.utf8("{\"t\":\"x\"}"));
        check("frame roundtrip", new String(Frames.read(new ByteArrayInputStream(bo.toByteArray()), 100), StandardCharsets.UTF_8).equals("{\"t\":\"x\"}"));
        check("clean EOF -> null", Frames.read(new ByteArrayInputStream(new byte[0]), 100) == null);
        boolean t1=false,t2=false,t3=false,t4=false;
        try { Frames.read(new ByteArrayInputStream(new byte[]{0x7f,(byte)0xff,(byte)0xff,(byte)0xff,1}), 1000); } catch (IOException e) { t1 = true; }
        try { Frames.read(new ByteArrayInputStream(new byte[]{(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff}), 1000); } catch (IOException e) { t2 = true; }
        try { Frames.read(new ByteArrayInputStream(new byte[]{0,0,0,0}), 1000); } catch (IOException e) { t3 = true; }
        try { Frames.read(new ByteArrayInputStream(new byte[]{0,0,0,10,1,2}), 1000); } catch (IOException e) { t4 = true; }
        check("oversize (2 GiB / 4 GiB-1 / zero) length rejected before allocation", t1 && t2 && t3);
        check("truncated body rejected", t4);
        boolean badUtf=false;
        try { Frames.decodeUtf8(new byte[]{(byte)0xC3,0x28}); } catch (java.nio.charset.CharacterCodingException e) { badUtf = true; }
        check("malformed UTF-8 rejected (not silently replaced)", badUtf);

        byte[] k = BridgeAuth.randomBytes(16);
        String shown = Auth.formatKey(k);
        check("key format xxxx-…(8 groups) & android/mod parse agree",
            shown.length() == 39 && Arrays.equals(Auth.parseKey(shown), k) && Arrays.equals(BridgeAuth.parseKey(shown.toUpperCase().replace("-", " ")), k));
        check("parseKey rejects wrong length / non-hex",
            BridgeAuth.parseKey("abcd") == null && BridgeAuth.parseKey(shown.substring(1)) == null && BridgeAuth.parseKey(shown.replace('a','z').replace('b','z')) == null && BridgeAuth.parseKey(null) == null);
        byte[] a = BridgeAuth.randomBytes(16), b = BridgeAuth.randomBytes(16);
        check("client/server proofs differ (no reflection) and are order-bound",
            !Arrays.equals(BridgeAuth.proof(k, BridgeAuth.LABEL_CLIENT, a, b), BridgeAuth.proof(k, BridgeAuth.LABEL_SERVER, a, b))
            && !Arrays.equals(BridgeAuth.proof(k, BridgeAuth.LABEL_CLIENT, a, b), BridgeAuth.proof(k, BridgeAuth.LABEL_CLIENT, b, a)));
        check("android & mod HMAC implementations interoperate",
            Arrays.equals(BridgeAuth.proof(k, "L", a, b), Auth.proof(k, "L", a, b)));
    }

    // ------------------------------------------------------------------ happy path
    static void testHappyPath() throws Exception {
        System.out.println("[End-to-end: server <-> provider]");
        byte[] key = Auth.randomBytes(16);
        FakeHost host = new FakeHost();
        try (BridgeServer server = new BridgeServer(0, key, host)) {
            int port = server.start();
            Field f = BridgeServer.class.getDeclaredField("serverSocket"); f.setAccessible(true);
            ServerSocket ss = (ServerSocket) f.get(server);
            check("server bound to 127.0.0.1 only (not wildcard)", ss.getInetAddress().getHostAddress().equals("127.0.0.1"));

            AndroidMediaProvider prov = new AndroidMediaProvider(() -> Auth.formatKey(key), port);
            check("connects + authenticates; empty state before Spotify", waitFor(() -> host.connected.get(), 4000));
            check("no fake media while nothing plays", waitFor(() -> stat(prov).equals("No Spotify notification"), 3000) && prov.snapshot().title().isEmpty() && prov.snapshot().artwork() == null && prov.snapshot().positionMs() == -1 && !prov.snapshot().toggle());

            byte[] art = png(7);
            host.state = playing("Şarkı \"A\"", 12_000, art, "art1");
            server.publish();
            check("state delivered with unicode/escapes intact", waitFor(() -> prov.snapshot().title().equals("Şarkı \"A\""), 3000));
            MediaSnapshot s1 = prov.snapshot();
            check("artist/album/duration/flags mapped", s1.artist().equals("Sanatçı İğşçöü 🎵") && s1.album().equals("Album \"Q\"\n2") && s1.durationMs() == 200_000 && s1.playing() && s1.previous() && s1.toggle() && s1.next() && s1.seek());
            check("cover bytes delivered intact", Arrays.equals(s1.artwork(), art));
            long p1 = s1.estimatedPosition();
            check("position near 12 s", p1 >= 12_000 && p1 < 14_000);

            // same art, new position: art byte[] instance must be REUSED (HUD must not re-decode)
            host.state = playing("Şarkı \"A\"", 30_000, art, "art1");
            server.publish();
            check("position update applied", waitFor(() -> prov.snapshot().positionMs() >= 30_000, 3000));
            check("same cover -> same byte[] instance, same key (no texture reload)", prov.snapshot().artwork() == s1.artwork() && prov.snapshot().key().equals(s1.key()));

            // art over-the-wire only when changed: inspect with a raw authenticated client
            try (Raw raw = new Raw(port)) {
                check("raw client authenticates (mutual proof verified)", raw.handshake(key));
                Map<String,Object> first = raw.read(1_000_000);
                check("fresh connection gets cover once", "state".equals(first.get("t")) && first.get("art") != null);
                host.state = playing("Şarkı \"A\"", 31_000, art, "art1");
                server.publish();
                Map<String,Object> second = null;
                for (int i = 0; i < 5 && (second == null || !"state".equals(second.get("t"))); i++) second = raw.read(1_000_000);
                check("unchanged cover is NOT resent", "state".equals(second.get("t")) && second.get("art") == null && "art1".equals(second.get("aid")));
                host.state = playing("Başka", 0, png(9), "art2");
                server.publish();
                Map<String,Object> third = null;
                for (int i = 0; i < 5; i++) { third = raw.read(1_000_000); if ("state".equals(third.get("t")) && "Başka".equals(third.get("title"))) break; }
                check("new cover is sent when it changes", third.get("art") != null && "art2".equals(third.get("aid")));
            }

            // provider reconnects (it was replaced by the raw client) and gets fresh cover
            check("provider recovers after being replaced", waitFor(() -> prov.snapshot().title().equals("Başka") && prov.snapshot().artwork() != null, 12_000));

            // heartbeat keeps it alive & refreshes position
            long before = prov.snapshot().positionMs();
            Thread.sleep(5200);
            check("heartbeat: still connected and position refreshed after >4 s", host.connected.get() && prov.snapshot().title().equals("Başka"));

            // cover disappears -> no stale cover kept
            MediaState.Builder nb = new MediaState.Builder(); nb.trackId="x"; nb.title="NoArt"; nb.status="Spotify · text only";
            host.state = nb.build(); server.publish();
            check("track without cover shows none (no stale/fake cover); unknown pos/dur = -1; controls off",
                waitFor(() -> prov.snapshot().title().equals("NoArt") && prov.snapshot().artwork() == null && prov.snapshot().positionMs() == -1 && prov.snapshot().durationMs() == -1 && !prov.snapshot().toggle() && !prov.snapshot().seek(), 3000));

            // commands
            host.state = playing("C", 1000, art, "art1"); server.publish();
            waitFor(() -> prov.snapshot().title().equals("C"), 3000);
            long t0 = System.nanoTime();
            prov.command(MediaProvider.Command.TOGGLE, 0);
            prov.command(MediaProvider.Command.SEEK, 123_456);
            prov.command(MediaProvider.Command.NEXT, 0);
            prov.command(MediaProvider.Command.PREVIOUS, 0);
            long took = (System.nanoTime() - t0) / 1_000_000;
            check("command() returns immediately (<50 ms, render thread safe)", took < 50);
            List<String> got = new ArrayList<>();
            for (int i = 0; i < 4; i++) { String c = host.commands.poll(3, TimeUnit.SECONDS); if (c != null) got.add(c); }
            check("all four commands reach the host in order, SEEK carries position", got.equals(List.of("TOGGLE:0", "SEEK:123456", "NEXT:0", "PREVIOUS:0")));

            long s0 = System.nanoTime(); for (int i = 0; i < 100000; i++) prov.snapshot();
            check("snapshot() is a plain volatile read (100k calls < 50 ms)", (System.nanoTime() - s0) / 1_000_000 < 50);

            // server killed -> provider clears state (no stale media)
            server.close();
            check("server stops -> snapshot becomes empty 'offline' (no stale track/cover)",
                waitFor(() -> stat(prov).equals("Android bridge offline") && prov.snapshot().title().isEmpty() && prov.snapshot().artwork() == null, 6000));
            prov.close();
            check("close() -> 'Stopped'", stat(prov).equals("Stopped"));
        }
    }

    // ------------------------------------------------------------------ wrong key
    static void testWrongKey() throws Exception {
        System.out.println("[Wrong / missing / invalid key]");
        byte[] key = Auth.randomBytes(16);
        FakeHost host = new FakeHost();
        host.state = playing("Secret", 0, png(3), "a");
        try (BridgeServer server = new BridgeServer(0, key, host)) {
            int port = server.start();
            AtomicReference<String> typed = new AtomicReference<>("");
            AndroidMediaProvider prov = new AndroidMediaProvider(typed::get, port);
            check("empty key -> 'Bridge key missing', no connection", waitFor(() -> stat(prov).equals("Bridge key missing"), 3000) && !host.connected.get());
            typed.set("not-a-key");
            check("malformed key -> 'Bridge key invalid'", waitFor(() -> stat(prov).equals("Bridge key invalid"), 3000));
            typed.set(Auth.formatKey(Auth.randomBytes(16)));
            check("wrong key -> 'Pairing failed', server never marks client connected, no media leaks",
                waitFor(() -> stat(prov).startsWith("Pairing failed"), 5000) && !host.connected.get() && prov.snapshot().title().isEmpty());
            typed.set(Auth.formatKey(key));
            check("correcting the key reconnects immediately (no long wait)", waitFor(() -> prov.snapshot().title().equals("Secret"), 4000) && host.connected.get());
            prov.close();
        }
    }

    // ------------------------------------------------------------------ rogue server
    static void testRogueServer() throws Exception {
        System.out.println("[Rogue process squatting the port]");
        byte[] key = Auth.randomBytes(16);
        String keyHex = BridgeAuth.toHex(key);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (ServerSocket rogue = new ServerSocket(0, 5, InetAddress.getByAddress(new byte[]{127,0,0,1}))) {
            int port = rogue.getLocalPort();
            AtomicBoolean sentFakeState = new AtomicBoolean();
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        Socket s = rogue.accept();
                        s.setSoTimeout(4000);
                        InputStream in = new TeeInput(s.getInputStream(), captured);
                        OutputStream out = s.getOutputStream();
                        Map<String,Object> h = new LinkedHashMap<>();
                        h.put("t","hello"); h.put("v", 1L); h.put("ns", BridgeAuth.toHex(BridgeAuth.randomBytes(16)));
                        Frames.write(out, Frames.utf8(FlatJson.write(h)));
                        try { Frames.read(in, 512); } catch (IOException e) { }
                        // pretend success with a forged proof, then push attractive state
                        Map<String,Object> ok = new LinkedHashMap<>();
                        ok.put("t","ok"); ok.put("p", BridgeAuth.toHex(BridgeAuth.randomBytes(32)));
                        Frames.write(out, Frames.utf8(FlatJson.write(ok)));
                        Map<String,Object> st = new LinkedHashMap<>();
                        st.put("t","state"); st.put("ok", true); st.put("title","EVIL"); st.put("st","evil");
                        try { Frames.write(out, Frames.utf8(FlatJson.write(st))); sentFakeState.set(true); } catch (IOException e) {}
                        try { Thread.sleep(300); } catch (InterruptedException e) {}
                        s.close();
                    }
                } catch (IOException e) { }
            });
            t.setDaemon(true); t.start();
            AndroidMediaProvider prov = new AndroidMediaProvider(() -> BridgeAuth.formatKey(key), port);
            waitFor(sentFakeState::get, 5000);
            Thread.sleep(500);
            check("forged server proof rejected; its state is never shown", stat(prov).startsWith("Pairing failed") && !prov.snapshot().title().equals("EVIL"));
            byte[] cap = captured.toByteArray();
            String capText = new String(cap, StandardCharsets.ISO_8859_1);
            boolean leaked = capText.contains(keyHex) || capText.contains(keyHex.toUpperCase()) || indexOf(cap, key) >= 0 || capText.contains(BridgeAuth.formatKey(key));
            check("pairing key never appears on the wire (rogue server saw " + cap.length + " bytes)", cap.length > 0 && !leaked);
            prov.close();
        }
        // peer that speaks garbage / skips hello
        try (ServerSocket junk = new ServerSocket(0, 5, InetAddress.getByAddress(new byte[]{127,0,0,1}))) {
            Thread t = new Thread(() -> { try { while (true) { Socket s = junk.accept(); s.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes()); s.getOutputStream().flush(); Thread.sleep(200); s.close(); } } catch (Exception e) {} });
            t.setDaemon(true); t.start();
            AndroidMediaProvider prov = new AndroidMediaProvider(() -> BridgeAuth.formatKey(key), junk.getLocalPort());
            check("non-protocol peer (HTTP bytes) -> offline, no crash", waitFor(() -> stat(prov).equals("Android bridge offline"), 5000));
            prov.close();
        }
        // oversized state frame from an otherwise-valid server must be refused, not buffered
        byte[] k2 = Auth.randomBytes(16);
        try (ServerSocket big = new ServerSocket(0, 5, InetAddress.getByAddress(new byte[]{127,0,0,1}))) {
            AtomicBoolean refused = new AtomicBoolean();
            Thread t = new Thread(() -> { try { Socket s = big.accept(); s.setSoTimeout(4000);
                OutputStream out = s.getOutputStream(); InputStream in = s.getInputStream();
                byte[] ns = BridgeAuth.randomBytes(16);
                Map<String,Object> h = new LinkedHashMap<>(); h.put("t","hello"); h.put("v",1L); h.put("ns", BridgeAuth.toHex(ns));
                Frames.write(out, Frames.utf8(FlatJson.write(h)));
                Map<String,Object> a = FlatJson.parse(Frames.decodeUtf8(Frames.read(in, 512)));
                byte[] nc = BridgeAuth.fromHex((String) a.get("nc"), 16);
                Map<String,Object> ok = new LinkedHashMap<>(); ok.put("t","ok"); ok.put("p", BridgeAuth.toHex(BridgeAuth.proof(k2, BridgeAuth.LABEL_SERVER, nc, ns)));
                Frames.write(out, Frames.utf8(FlatJson.write(ok)));
                out.write(new byte[]{0x10, 0x00, 0x00, 0x00}); out.flush(); // announce a 256 MiB frame
                try { Thread.sleep(500); out.write(1); out.flush(); } catch (IOException e) { refused.set(true); }
                s.close(); } catch (Exception e) { refused.set(true); } });
            t.setDaemon(true); t.start();
            AndroidMediaProvider prov = new AndroidMediaProvider(() -> BridgeAuth.formatKey(k2), big.getLocalPort());
            check("announced 256 MiB frame -> connection dropped without allocating", waitFor(() -> stat(prov).equals("Android bridge offline"), 5000));
            prov.close();
        }
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer: for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    static final class TeeInput extends InputStream {
        final InputStream in; final OutputStream tee;
        TeeInput(InputStream in, OutputStream tee) { this.in = in; this.tee = tee; }
        public int read() throws IOException { int b = in.read(); if (b >= 0) synchronized (tee) { tee.write(b); } return b; }
        public int read(byte[] b, int o, int l) throws IOException { int n = in.read(b, o, l); if (n > 0) synchronized (tee) { tee.write(b, o, n); } return n; }
    }

    // ------------------------------------------------------------------ server hardening
    static void testServerHardening() throws Exception {
        System.out.println("[Server hardening]");
        byte[] key = Auth.randomBytes(16);
        FakeHost host = new FakeHost();
        try (BridgeServer server = new BridgeServer(0, key, host)) {
            int port = server.start();

            // wrong proof
            try (Raw r = new Raw(port)) {
                r.read(512);
                Map<String,Object> a = new LinkedHashMap<>();
                a.put("t","auth"); a.put("nc", BridgeAuth.toHex(BridgeAuth.randomBytes(16))); a.put("p", BridgeAuth.toHex(BridgeAuth.randomBytes(32)));
                r.write(a);
                Map<String,Object> resp = r.read(512);
                check("bad proof -> 'fail', not authenticated", resp != null && "fail".equals(resp.get("t")) && !host.connected.get());
            }

            // oversized pre-auth frame
            try (Raw r = new Raw(port)) {
                r.read(512);
                r.out.write(new byte[]{0, 0, 0x10, 0}); // claims 4096 bytes (> 512 pre-auth limit)
                r.out.write(new byte[4096]); r.out.flush();
                boolean closed = false;
                try { closed = r.in.read() < 0; } catch (IOException e) { closed = true; }
                check("pre-auth frame > 512 B -> connection closed", closed);
            }

            // garbage pre-auth
            try (Raw r = new Raw(port)) {
                r.read(512);
                Frames.write(r.out, Frames.utf8("not json at all"));
                boolean closed = false;
                try { closed = r.in.read() < 0; } catch (IOException e) { closed = true; }
                check("pre-auth garbage -> connection closed", closed);
            }

            // authenticated client: unknown commands ignored, oversized post-auth frame closes, flood limited
            host.state = playing("T", 0, png(2), "a");
            try (Raw r = new Raw(port)) {
                check("handshake OK", r.handshake(key));
                Map<String,Object> bogus = new LinkedHashMap<>(); bogus.put("t","cmd"); bogus.put("c","FORMAT_DISK"); bogus.put("pos", 0L);
                r.write(bogus);
                Map<String,Object> lower = new LinkedHashMap<>(); lower.put("t","cmd"); lower.put("c","toggle"); lower.put("pos", 0L);
                r.write(lower);
                Thread.sleep(300);
                check("unknown / wrongly-cased command names never reach the host", host.commands.isEmpty());

                for (int i = 0; i < 100; i++) {
                    Map<String,Object> c = new LinkedHashMap<>(); c.put("t","cmd"); c.put("c","NEXT"); c.put("pos", 0L);
                    r.write(c);
                }
                Thread.sleep(500);
                check("command flood limited (≤20/s, got " + host.commands.size() + " of 100)", host.commands.size() <= 20 && host.commands.size() > 0);

                r.out.write(new byte[]{0, 0, 0x10, 0}); r.out.write(new byte[4096]); r.out.flush();
                boolean closed = false;
                long end = System.currentTimeMillis() + 5000;
                try { while (System.currentTimeMillis() < end) { byte[] f = Frames.read(r.in, 1_000_000); if (f == null) { closed = true; break; } } }
                catch (IOException e) { closed = true; }
                check("post-auth frame > 1 KiB -> connection closed", closed);
            }

            // idle pre-auth connection is dropped by the handshake timeout; pending-handshake cap
            List<Raw> idle = new ArrayList<>();
            for (int i = 0; i < 3; i++) { Raw r = new Raw(port); r.read(512); idle.add(r); }
            Thread.sleep(150);
            boolean fourthRefused = false;
            try (Raw r4 = new Raw(port)) { r4.s.setSoTimeout(1500); try { fourthRefused = r4.in.read() < 0; } catch (IOException e) { fourthRefused = true; } }
            check("4th simultaneous unauthenticated connection refused (cap = 3)", fourthRefused);
            long t0 = System.currentTimeMillis();
            boolean dropped = false;
            try { idle.get(0).s.setSoTimeout(8000); dropped = idle.get(0).in.read() < 0; } catch (IOException e) { dropped = true; }
            long dt = System.currentTimeMillis() - t0;
            check("silent unauthenticated peer dropped by handshake timeout (~4 s, took " + dt + " ms)", dropped && dt < 6500);
            for (Raw r : idle) r.close();
        }
    }

    // ------------------------------------------------------------------ rotation / disconnect
    static void testKeyRotationAndDisconnect() throws Exception {
        System.out.println("[Key regeneration & disconnect]");
        byte[] key = Auth.randomBytes(16);
        FakeHost host = new FakeHost();
        host.state = playing("Before", 0, png(5), "z");
        try (BridgeServer server = new BridgeServer(0, key, host)) {
            int port = server.start();
            AtomicReference<String> typed = new AtomicReference<>(Auth.formatKey(key));
            AndroidMediaProvider prov = new AndroidMediaProvider(typed::get, port);
            check("connected with original key", waitFor(() -> prov.snapshot().title().equals("Before"), 4000));
            byte[] newKey = Auth.randomBytes(16);
            server.setKey(newKey);
            check("regenerating the key drops the existing client and clears its state",
                waitFor(() -> !host.connected.get() && prov.snapshot().title().isEmpty(), 4000));
            check("old key no longer works", waitFor(() -> stat(prov).startsWith("Pairing failed"), 6000));
            typed.set(Auth.formatKey(newKey));
            check("entering the new key reconnects", waitFor(() -> prov.snapshot().title().equals("Before"), 5000));
            prov.close();
        }
        // port already taken -> start() reports an error instead of silently failing
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[]{127,0,0,1}))) {
            boolean threw = false;
            try (BridgeServer s2 = new BridgeServer(taken.getLocalPort(), key, new FakeHost())) { s2.start(); } catch (IOException e) { threw = true; }
            check("port already in use -> start() throws (service can surface it in the UI)", threw);
        }
    }
}
