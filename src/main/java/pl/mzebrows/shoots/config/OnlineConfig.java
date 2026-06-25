// src/main/java/pl/mzebrows/shoots/config/OnlineConfig.java
package pl.mzebrows.shoots.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Online-mode settings read from {@code game.properties} (see OnlineMode.md, F6). LAN host/join needs no
 * address (discovery handles it); {@code online.host}/{@code online.port} are the INTERNET join target
 * (typed-IP entry was intentionally deferred). {@code online.mode=off} (default) keeps the game offline.
 */
public record OnlineConfig(String mode, String host, int port) {

    private static final String RESOURCE = "game.properties";

    public boolean isHost() {
        return "host".equalsIgnoreCase(mode);
    }

    public boolean isClient() {
        return "client".equalsIgnoreCase(mode);
    }

    public boolean isOnline() {
        return isHost() || isClient();
    }

    /** Whether an explicit internet host address is configured (else a client uses LAN discovery). */
    public boolean hasHostAddress() {
        return host != null && !host.isBlank();
    }

    /** Loads online settings from the bundled {@code game.properties}; absent keys fall back to defaults. */
    public static OnlineConfig load() {
        var props = new Properties();
        try (InputStream in = OnlineConfig.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // fall back to defaults below
        }
        return new OnlineConfig(
                props.getProperty("online.mode", "off").trim(),
                props.getProperty("online.host", "").trim(),
                parsePort(props.getProperty("online.port", "48900")));
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 48900;
        }
    }
}
