// src/main/java/pl/mzebrows/shoots/net/LanSearch.java
package pl.mzebrows.shoots.net;

import java.net.SocketException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * A running LAN search for a host on a known port (F7d). Wraps {@link LanDiscovery} and, per the v1
 * "one LAN game per port" assumption, returns the FIRST joinable match whose advertised TCP port equals the
 * target. {@link #poll()} is non-blocking so the menu can drive it each frame (keeping the spinner + ESC
 * responsive) and emits a throttled progress log (~1/s) for debugging discovery. AWT-free.
 */
@Slf4j
public final class LanSearch implements AutoCloseable {

    private static final long DISCOVERY_TTL_MS = 4000;
    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final LanDiscovery discovery;
    private final int targetPort;
    private final long startNanos = System.nanoTime();
    private long lastLogNanos;

    /** Opens the UDP discovery listener and begins searching for a host on {@code targetPort}. */
    public LanSearch(int targetPort) throws SocketException {
        this.targetPort = targetPort;
        this.discovery = new LanDiscovery(DISCOVERY_TTL_MS);
        this.discovery.start();
        log.info("Searching LAN for a game on port {}", targetPort);
    }

    /** The first joinable discovered match on the target port, or {@code null} while still searching. */
    public DiscoveredMatch poll() {
        List<DiscoveredMatch> matches = discovery.matches();
        logProgress(matches.size());
        for (DiscoveredMatch match : matches) {
            if (match.port() == targetPort && match.joinable()) {
                log.info("Discovered LAN match {} at {}:{}", match.matchCode(), match.host(), match.port());
                return match;
            }
        }
        return null;
    }

    public int targetPort() {
        return targetPort;
    }

    /** Whole seconds elapsed since the search started (for the on-screen caption). */
    public long elapsedSeconds() {
        return (System.nanoTime() - startNanos) / 1_000_000_000L;
    }

    private void logProgress(int candidateCount) {
        long now = System.nanoTime();
        if (now - lastLogNanos >= LOG_INTERVAL_NANOS) {
            lastLogNanos = now;
            log.debug("Searching LAN game on port {} ({}s elapsed, {} candidate(s) seen)",
                    targetPort, elapsedSeconds(), candidateCount);
        }
    }

    @Override
    public void close() {
        discovery.close();
    }
}
