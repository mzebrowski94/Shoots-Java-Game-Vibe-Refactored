// src/main/java/pl/mzebrows/shoots/config/OnlineConfig.java
package pl.mzebrows.shoots.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Online-mode settings read from {@code game.properties} (see OnlineMode.md, F7). The mode (host / join) is
 * now chosen in the menu, not via a property, so only the connection defaults live here: {@code online.port}
 * is the host's listen port (and the port LAN search scans), and {@code online.host} is the default JOIN
 * ONLINE target shown in the menu ({@code 127.0.0.1} when blank, for same-machine testing).
 */
public record OnlineConfig(String host, int port) {

    /** Default join target when {@code online.host} is blank (same-machine play). */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default host listen / LAN-search port when {@code online.port} is absent or invalid. */
    public static final int DEFAULT_PORT = 48900;

    private static final String RESOURCE = "game.properties";

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
        String host = props.getProperty("online.host", "").trim();
        if (host.isEmpty()) {
            host = DEFAULT_HOST;
        }
        return new OnlineConfig(host, parsePort(props.getProperty("online.port", String.valueOf(DEFAULT_PORT))));
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
