// src/main/java/pl/mzebrows/shoots/net/LanAnnouncement.java
package pl.mzebrows.shoots.net;

import java.nio.charset.StandardCharsets;

/**
 * The payload a host beacons over UDP so LAN clients can auto-discover the match (see OnlineMode.md, F4).
 * It deliberately omits the host IP -- the receiver pairs it with the UDP packet's source address, which
 * is more reliable than a host guessing its own LAN interface. A {@code MAGIC} prefix lets a listener
 * ignore unrelated UDP traffic. Internet play has no equivalent (manual {@code IP:port}).
 */
public record LanAnnouncement(String matchCode, String hostName, int port, int players, boolean joinable) {

    private static final String MAGIC = "SHOOOTS1";
    private static final String SEP = "|";

    public LanAnnouncement {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }

    /** The single-line beacon text (UTF-8 of this is what travels in the datagram). */
    public String encode() {
        return MAGIC + SEP + "code=" + matchCode
                + SEP + "name=" + sanitize(hostName)
                + SEP + "port=" + port
                + SEP + "players=" + players
                + SEP + "join=" + joinable;
    }

    public byte[] toBytes() {
        return encode().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses bytes from a received datagram, or {@code null} if it is not a well-formed Shooots beacon. */
    public static LanAnnouncement fromBytes(byte[] data, int length) {
        return decode(new String(data, 0, length, StandardCharsets.UTF_8));
    }

    /** Parses a beacon line, returning {@code null} for anything that is not a valid Shooots beacon. */
    public static LanAnnouncement decode(String line) {
        if (line == null || !line.startsWith(MAGIC + SEP)) {
            return null;
        }
        try {
            String code = null;
            String name = "";
            int port = -1;
            int players = -1;
            boolean joinable = false;
            for (String token : line.split("\\" + SEP)) {
                int eq = token.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = token.substring(0, eq);
                String value = token.substring(eq + 1);
                switch (key) {
                    case "code" -> code = value;
                    case "name" -> name = value;
                    case "port" -> port = Integer.parseInt(value);
                    case "players" -> players = Integer.parseInt(value);
                    case "join" -> joinable = Boolean.parseBoolean(value);
                    default -> { /* ignore unknown keys for forward-compat */ }
                }
            }
            if (code == null || port < 1 || port > 65535 || players < 1) {
                return null;
            }
            return new LanAnnouncement(code, name, port, players, joinable);
        } catch (RuntimeException _) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[|=\\r\\n]", " ").trim();
    }
}
