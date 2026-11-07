package com.winnerclient.fabric.media.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, strict, dependency-free JSON for FLAT objects only
 * (string / integer / decimal / boolean / null values; no nesting, no arrays).
 * Used so the protocol core has no Gson / org.json dependency and can be unit-tested on a plain JVM.
 */
public final class FlatJson {
    private static final int MAX_KEYS = 32;

    private FlatJson() {}

    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                quote(sb, (String) v);
            } else if (v instanceof Boolean) {
                sb.append(((Boolean) v) ? "true" : "false");
            } else if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
                sb.append(((Number) v).longValue());
            } else if (v instanceof Double || v instanceof Float) {
                double d = ((Number) v).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("non-finite number");
                sb.append(d);
            } else {
                throw new IllegalArgumentException("unsupported value type: " + v.getClass().getName());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** @throws IllegalArgumentException on any deviation from the flat-object grammar. */
    public static Map<String, Object> parse(String s) {
        Parser p = new Parser(s);
        Map<String, Object> out = p.object();
        p.skipWs();
        if (p.i != s.length()) throw new IllegalArgumentException("trailing data");
        return out;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++; else break;
            }
        }

        Map<String, Object> object() {
            skipWs();
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                Object val = value();
                if (m.size() >= MAX_KEYS) throw new IllegalArgumentException("too many keys");
                if (m.containsKey(key)) throw new IllegalArgumentException("duplicate key");
                m.put(key, val);
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == '}') return m;
                throw new IllegalArgumentException("expected , or }");
            }
        }

        Object value() {
            char c = peek();
            if (c == '"') return string();
            if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i))  { i += 4; return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw new IllegalArgumentException("unsupported value");
        }

        Object number() {
            int start = i;
            if (peek() == '-') i++;
            int digits = 0;
            while (i < s.length() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) { i++; digits++; }
            if (digits == 0) throw new IllegalArgumentException("bad number");
            boolean integral = true;
            if (i < s.length() && s.charAt(i) == '.') {
                integral = false; i++;
                int f = 0;
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') { i++; f++; }
                if (f == 0) throw new IllegalArgumentException("bad number");
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                integral = false; i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                int x = 0;
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') { i++; x++; }
                if (x == 0) throw new IllegalArgumentException("bad number");
            }
            String t = s.substring(start, i);
            try {
                if (integral) return Long.valueOf(Long.parseLong(t));
                double d = Double.parseDouble(t);
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("bad number");
                return Double.valueOf(d);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad number");
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new IllegalArgumentException("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c < 0x20) throw new IllegalArgumentException("control char in string");
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) throw new IllegalArgumentException("bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw new IllegalArgumentException("bad unicode escape");
                        int cp = 0;
                        for (int k = 0; k < 4; k++) {
                            int d = Character.digit(s.charAt(i + k), 16);
                            if (d < 0) throw new IllegalArgumentException("bad unicode escape");
                            cp = (cp << 4) | d;
                        }
                        i += 4;
                        sb.append((char) cp);
                        break;
                    }
                    default: throw new IllegalArgumentException("bad escape");
                }
            }
        }

        char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(i);
        }

        char next() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(i++);
        }

        void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c);
        }
    }
}
