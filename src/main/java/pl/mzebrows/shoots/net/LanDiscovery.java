// src/main/java/pl/mzebrows/shoots/net/LanDiscovery.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Client-side LAN discovery: listens for host {@link LanAnnouncement} beacons over UDP and keeps a LIVE
 * list of matches, expiring entries whose beacons have stopped (so a closed host drops off the list).
 * The table logic ({@link #record}, {@link #matches(long)}) is pure and unit-testable with an injected
 * clock; the UDP listener is a thin wrapper over it. See OnlineMode.md (F4). Internet play has no
 * discovery -- the joiner uses a manual {@code IP:port} ({@link HostAddress}).
 */
@Slf4j
public final class LanDiscovery implements AutoCloseable {

    /** Well-known UDP port hosts beacon on and clients listen on. */
    public static final int DISCOVERY_PORT = 48888;

    private static final int BUFFER_SIZE = 1024;

    private final long ttlNanos;
    private final Map<String, DiscoveredMatch> byKey = new ConcurrentHashMap<>();
    private DatagramSocket socket;
    private Thread listener;
    private volatile boolean running;

    public LanDiscovery(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be > 0: " + ttlMillis);
        }
        this.ttlNanos = ttlMillis * 1_000_000L;
    }

    /**
     * Records (or refreshes) a beacon heard from {@code sourceHost} at {@code nowNanos}. Called by the
     * UDP listener with the packet's source address; exposed for headless tests.
     */
    public void record(String sourceHost, LanAnnouncement announcement, long nowNanos) {
        String key = announcement.matchCode() + "@" + sourceHost + ":" + announcement.port();
        boolean firstSighting = !byKey.containsKey(key);
        byKey.put(key, new DiscoveredMatch(announcement.matchCode(), announcement.hostName(), sourceHost,
                announcement.port(), announcement.players(), announcement.joinable(), nowNanos));
        if (firstSighting) {
            log.info("LAN discovery: found match {} from {}:{} (host '{}', {} players, joinable {})",
                    announcement.matchCode(), sourceHost, announcement.port(), announcement.hostName(),
                    announcement.players(), announcement.joinable());
        }
    }

    /** Matches still alive as of {@code nowNanos} (beacon seen within the TTL), ordered by match code. */
    public List<DiscoveredMatch> matches(long nowNanos) {
        return byKey.values().stream()
                .filter(m -> nowNanos - m.lastSeenNanos() <= ttlNanos)
                .sorted(Comparator.comparing(DiscoveredMatch::matchCode))
                .toList();
    }

    /** Live matches as of now. */
    public List<DiscoveredMatch> matches() {
        return matches(System.nanoTime());
    }

    /** Starts the UDP listener on {@code port} ({@code 0} = an ephemeral port, for tests). */
    public void start(int port) throws SocketException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        running = true;
        log.info("LAN discovery listening for host beacons on UDP port {}", socket.getLocalPort());
        listener = new Thread(this::listenLoop, "lan-discovery");
        listener.setDaemon(true);
        listener.start();
    }

    /** Starts the UDP listener on the well-known {@link #DISCOVERY_PORT}. */
    public void start() throws SocketException {
        start(DISCOVERY_PORT);
    }

    /** The bound listening port (resolves an ephemeral port), or {@code -1} if not started. */
    public int port() {
        return socket == null ? -1 : socket.getLocalPort();
    }

    private void listenLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            var packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                LanAnnouncement announcement = LanAnnouncement.fromBytes(packet.getData(), packet.getLength());
                if (announcement != null) {
                    record(packet.getAddress().getHostAddress(), announcement, System.nanoTime());
                } else {
                    log.debug("Ignored non-beacon UDP packet ({} bytes) from {}",
                            packet.getLength(), packet.getAddress().getHostAddress());
                }
            } catch (IOException e) {
                if (running) {
                    log.debug("Discovery listener stopped: {}", e.toString());
                }
                return;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }
}
