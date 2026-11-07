package com.winnerclient.fabric.media.bridge;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mutual challenge-response authentication. The pairing key is NEVER sent over the socket.
 *
 *   server -> client : hello   {ns}                       (server nonce, 16 random bytes)
 *   client -> server : auth    {nc, p = HMAC(key, "WC-BRIDGE-1 client" || ns || nc)}
 *   server -> client : ok      {p = HMAC(key, "WC-BRIDGE-1 server" || nc || ns)}
 *
 * Both sides verify the other's proof in constant time. Different labels per direction
 * prevent reflecting a proof back. A process that squats the port without the key cannot
 * produce a valid server proof, and learns nothing usable about the key (128-bit key).
 */
public final class BridgeAuth {
    public static final int KEY_BYTES = 16;
    public static final int NONCE_BYTES = 16;
    public static final String LABEL_CLIENT = "WC-BRIDGE-1 client";
    public static final String LABEL_SERVER = "WC-BRIDGE-1 server";

    private static final SecureRandom RNG = new SecureRandom();

    private BridgeAuth() {}

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static byte[] proof(byte[] key, String label, byte[] first, byte[] second) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(label.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            mac.update(first);
            mac.update(second);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    public static String toHex(byte[] b) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = hex[(b[i] >> 4) & 0xF];
            out[i * 2 + 1] = hex[b[i] & 0xF];
        }
        return new String(out);
    }

    /** Strict hex: exactly expectedBytes*2 hex digits, otherwise null. */
    public static byte[] fromHex(String s, int expectedBytes) {
        if (s == null || s.length() != expectedBytes * 2) return null;
        byte[] out = new byte[expectedBytes];
        for (int i = 0; i < expectedBytes; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Human-entered key: ignores dashes/spaces/case. Returns null unless exactly 32 hex digits. */
    public static byte[] parseKey(String typed) {
        if (typed == null) return null;
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c == '-' || c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
            sb.append(Character.toLowerCase(c));
        }
        return fromHex(sb.toString(), KEY_BYTES);
    }

    /** Display form: xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx */
    public static String formatKey(byte[] key) {
        String hex = toHex(key);
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) sb.append('-');
            sb.append(hex, i, i + 4);
        }
        return sb.toString();
    }
}
