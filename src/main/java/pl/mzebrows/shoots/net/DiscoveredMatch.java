// src/main/java/pl/mzebrows/shoots/net/DiscoveredMatch.java
package pl.mzebrows.shoots.net;

/**
 * A LAN match a client has heard a beacon from: the announced details plus the host's source IP (taken
 * from the UDP packet) and when it was last seen (for expiry). The client connects to {@link #host()}:
 * {@link #port()} via {@link TcpClientTransport}. See OnlineMode.md (F4).
 */
public record DiscoveredMatch(
        String matchCode,
        String hostName,
        String host,
        int port,
        int players,
        boolean joinable,
        long lastSeenNanos) {
}
