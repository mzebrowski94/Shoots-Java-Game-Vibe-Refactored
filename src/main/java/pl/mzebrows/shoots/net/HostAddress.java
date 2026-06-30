// src/main/java/pl/mzebrows/shoots/net/HostAddress.java
package pl.mzebrows.shoots.net;

/**
 * A parsed {@code host:port} for the INTERNET join path (the joiner types a public IP / VPN-LAN address
 * since nothing broadcasts across the internet -- see OnlineMode.md, F4). IPv4/hostname only for v1.
 */
public record HostAddress(String host, int port) {

    public HostAddress {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }

    /**
     * Parses {@code "host:port"} (e.g. {@code "203.0.113.7:48900"}). Throws {@link IllegalArgumentException}
     * if the host or port is missing or invalid.
     */
    public static HostAddress parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("address must not be null");
        }
        String trimmed = text.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            throw new IllegalArgumentException("expected host:port, got: " + text);
        }
        String host = trimmed.substring(0, colon).trim();
        int port;
        try {
            port = Integer.parseInt(trimmed.substring(colon + 1).trim());
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("invalid port in: " + text);
        }
        return new HostAddress(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
