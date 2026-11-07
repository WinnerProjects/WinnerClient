package com.winnerclient.fabric.media.bridge;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Length-prefixed framing: 4-byte big-endian length, then that many bytes of UTF-8 JSON.
 * The length is validated against a caller-supplied limit BEFORE any buffer is allocated.
 */
public final class Frames {
    /** Absolute upper bound for anything we will ever send (album art is the only large frame). */
    public static final int HARD_MAX = 1_500_000;

    private Frames() {}

    /** Writes header+payload with a single write call. Caller must serialise concurrent writers. */
    public static void write(OutputStream out, byte[] payload) throws IOException {
        if (payload.length == 0 || payload.length > HARD_MAX) {
            throw new IOException("frame size out of range");
        }
        byte[] buf = new byte[4 + payload.length];
        buf[0] = (byte) (payload.length >>> 24);
        buf[1] = (byte) (payload.length >>> 16);
        buf[2] = (byte) (payload.length >>> 8);
        buf[3] = (byte) payload.length;
        System.arraycopy(payload, 0, buf, 4, payload.length);
        out.write(buf);
        out.flush();
    }

    /** @return payload, or null on clean EOF at a frame boundary. */
    public static byte[] read(InputStream in, int maxLen) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read(), b2 = in.read(), b3 = in.read();
        if ((b1 | b2 | b3) < 0) throw new EOFException("truncated frame header");
        long len = ((long) b0 << 24) | ((long) b1 << 16) | ((long) b2 << 8) | b3;
        if (len <= 0 || len > maxLen || len > HARD_MAX) {
            throw new IOException("frame length out of range");
        }
        byte[] data = new byte[(int) len];
        int off = 0;
        while (off < data.length) {
            int n = in.read(data, off, data.length - off);
            if (n < 0) throw new EOFException("truncated frame body");
            off += n;
        }
        return data;
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Strict decode: malformed UTF-8 is an error, never silently replaced. */
    public static String decodeUtf8(byte[] data) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString();
    }
}
