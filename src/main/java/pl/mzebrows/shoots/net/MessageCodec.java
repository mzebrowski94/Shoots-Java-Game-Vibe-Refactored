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
 * Serialises NetMessages to a single human-readable line (TYPE|key=value|...) and frames them on a
 * stream with a 4-byte big-endian length prefix. Text over JSON/binary for zero-dependency debuggability;
 * the framing makes it safe over TCP, where reads arrive in arbitrary chunks. See OnlineMode.md.
 */
public final class MessageCodec {

    private static final String SEP = "|";
    private static final String SLOT_SEP = ";";
    private static final String FIELD_SEP = ",";

    private MessageCodec() {
    }

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
            case NetMessage.Lobby m ->
                    "LOBBY" + SEP + "names=" + encodeNames(m.slotNames());
            case NetMessage.Start m ->
                    "START" + SEP + "seed=" + m.seed() + SEP + "slots=" + encodeInts(m.orderedSlots())
                            + SEP + "rt=" + m.roundTimeSeconds() + SEP + "rl=" + m.roundLimit();
            case NetMessage.Pause m ->
                    "PAUSE" + SEP + "slot=" + m.slot() + SEP + "paused=" + m.paused();
        };
    }

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
            case "LOBBY" -> new NetMessage.Lobby(decodeNames(f.getOrDefault("names", "")));
            case "START" -> new NetMessage.Start(longField(f, "seed"), decodeInts(f.getOrDefault("slots", "")),
                    Integer.parseInt(f.getOrDefault("rt", "0")), Integer.parseInt(f.getOrDefault("rl", "0")));
            case "PAUSE" -> new NetMessage.Pause(intField(f, "slot"), Boolean.parseBoolean(require(f, "paused")));
            default -> throw new IllegalArgumentException("unknown message type: " + type);
        };
    }

    public static void writeFrame(OutputStream out, NetMessage message) throws IOException {
        byte[] body = encode(message).getBytes(StandardCharsets.UTF_8);
        out.write(new byte[] {
                (byte) (body.length >>> 24), (byte) (body.length >>> 16),
                (byte) (body.length >>> 8), (byte) body.length });
        out.write(body);
        out.flush();
    }

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

    private static String encodeNames(String[] names) {
        var joiner = new StringJoiner(SLOT_SEP);
        for (String name : names) {
            joiner.add(sanitize(name));
        }
        return joiner.toString();
    }

    private static String[] decodeNames(String raw) {
        return raw.isEmpty() ? new String[0] : raw.split(SLOT_SEP, -1);
    }

    private static String encodeInts(int[] values) {
        var joiner = new StringJoiner(SLOT_SEP);
        for (int v : values) {
            joiner.add(Integer.toString(v));
        }
        return joiner.toString();
    }

    private static int[] decodeInts(String raw) {
        if (raw.isEmpty()) {
            return new int[0];
        }
        String[] parts = raw.split(SLOT_SEP, -1);
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i]);
        }
        return values;
    }

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
