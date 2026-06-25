// src/main/java/pl/mzebrows/shoots/net/MessageCodec.java
package pl.mzebrows.shoots.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Serialises {@link NetMessage}s to a single, human-readable line ({@code TYPE|key=value|...}) and frames
 * them on a stream with a 4-byte big-endian length prefix. Text was chosen over JSON/binary for
 * debuggability with zero extra dependencies (the messages are tiny and flat); the framing makes the
 * format safe over TCP, where reads arrive in arbitrary chunks. See {@code OnlineMode.md}.
 */
public final class MessageCodec {

    private static final String SEP = "|";
    private static final String SLOT_SEP = ";";
    private static final String FIELD_SEP = ",";

    private MessageCodec() {
    }

    // -- line encoding ------------------------------------------------------

    /** Encodes a message to its single-line wire form (no trailing newline). */
    public static String encode(NetMessage message) {
        return switch (message) {
            case NetMessage.Join m ->
                    "JOIN" + SEP + "name=" + sanitize(m.name()) + SEP + "ver=" + m.protocolVersion();
            case NetMessage.Welcome m ->
                    "WELCOME" + SEP + "slot=" + m.slot() + SEP + "players=" + m.playerCount()
                            + SEP + "seed=" + m.seed() + SEP + "code=" + m.matchCode();
            case NetMessage.Input m ->
                    "INPUT" + SEP + "frame=" + m.frame() + SEP + "in=" + encodeTick(m.input());
            case NetMessage.Frame m ->
                    "FRAME" + SEP + "frame=" + m.frame() + SEP + "in=" + encodeSlots(m.bySlot());
            case NetMessage.Control m ->
                    "CONTROL" + SEP + "frame=" + m.frame() + SEP + "kind=" + m.kind().name();
            case NetMessage.Hash m ->
                    "HASH" + SEP + "frame=" + m.frame() + SEP + "hash=" + m.hash();
        };
    }

    /** Parses a single-line wire form back into a message. */
    public static NetMessage decode(String line) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("empty message line");
        }
        String[] parts = line.split("\\" + SEP);
        String type = parts[0];
        Map<String, String> f = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("malformed field: " + parts[i]);
            }
            f.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
        }
        return switch (type) {
            case "JOIN" -> new NetMessage.Join(f.getOrDefault("name", ""), intField(f, "ver"));
            case "WELCOME" -> new NetMessage.Welcome(
                    intField(f, "slot"), intField(f, "players"), longField(f, "seed"), f.get("code"));
            case "INPUT" -> new NetMessage.Input(longField(f, "frame"), decodeTick(f.get("in")));
            case "FRAME" -> new NetMessage.Frame(longField(f, "frame"), decodeSlots(f.get("in")));
            case "CONTROL" -> new NetMessage.Control(
                    longField(f, "frame"), ControlEvent.Kind.valueOf(f.get("kind")));
            case "HASH" -> new NetMessage.Hash(longField(f, "frame"), longField(f, "hash"));
            default -> throw new IllegalArgumentException("unknown message type: " + type);
        };
    }

    // -- stream framing (length-prefixed) -----------------------------------

    /** Writes {@code message} as a length-prefixed frame and flushes. */
    public static void writeFrame(OutputStream out, NetMessage message) throws IOException {
        byte[] body = encode(message).getBytes(StandardCharsets.UTF_8);
        out.write(new byte[] {
                (byte) (body.length >>> 24), (byte) (body.length >>> 16),
                (byte) (body.length >>> 8), (byte) body.length });
        out.write(body);
        out.flush();
    }

    /**
     * Reads exactly one frame from {@code in}, tolerating reads that arrive in arbitrary chunks.
     * Returns {@code null} on a clean end-of-stream (no bytes left); throws {@link EOFException} if a
     * frame is truncated mid-way.
     */
    public static NetMessage readFrame(InputStream in) throws IOException {
        byte[] header = in.readNBytes(4);
        if (header.length == 0) {
            return null;
        }
        if (header.length < 4) {
            throw new EOFException("truncated length prefix (" + header.length + " bytes)");
        }
        int len = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16)
                | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        if (len < 0 || len > (1 << 20)) {
            throw new IOException("implausible frame length: " + len);
        }
        byte[] body = in.readNBytes(len);
        if (body.length < len) {
            throw new EOFException("truncated frame body (" + body.length + "/" + len + ")");
        }
        return decode(new String(body, StandardCharsets.UTF_8));
    }

    // -- helpers ------------------------------------------------------------

    private static String encodeTick(TickInput t) {
        return t.aim().name() + FIELD_SEP + t.shootHeld();
    }

    private static TickInput decodeTick(String token) {
        String[] kv = token.split(FIELD_SEP);
        return new TickInput(PlayWorld.AimInput.valueOf(kv[0]), Boolean.parseBoolean(kv[1]));
    }

    private static String encodeSlots(TickInput[] bySlot) {
        var joiner = new StringJoiner(SLOT_SEP);
        for (TickInput t : bySlot) {
            joiner.add(encodeTick(t));
        }
        return joiner.toString();
    }

    private static TickInput[] decodeSlots(String token) {
        String[] slots = token.split(SLOT_SEP);
        TickInput[] result = new TickInput[slots.length];
        for (int i = 0; i < slots.length; i++) {
            result[i] = decodeTick(slots[i]);
        }
        return result;
    }

    /** Strips wire-reserved characters from a free-text field (e.g. a player name). */
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[|=;,\\r\\n]", " ").trim();
    }

    private static int intField(Map<String, String> f, String key) {
        return Integer.parseInt(require(f, key));
    }

    private static long longField(Map<String, String> f, String key) {
        return Long.parseLong(require(f, key));
    }

    private static String require(Map<String, String> f, String key) {
        String v = f.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return v;
    }
}
