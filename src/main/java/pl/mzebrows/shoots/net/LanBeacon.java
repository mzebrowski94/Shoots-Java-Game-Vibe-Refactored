// src/main/java/pl/mzebrows/shoots/net/LanBeacon.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Host-side LAN beacon: periodically sends a {@link LanAnnouncement} over UDP so clients can
 * auto-discover the match (see OnlineMode.md, F4). Production uses {@link #broadcast}; the explicit-target
 * constructor lets tests beacon to {@code 127.0.0.1}. The announcement is re-read from the supplier each
 * tick, so a changing player count / joinable flag is reflected automatically.
 *
 * <p><b>Broadcast targeting (#0):</b> a single send to the limited broadcast {@code 255.255.255.255} only
 * leaves the host's default-route interface, so on a machine with several adapters (Wi-Fi + Ethernet +
 * VM/VPN virtual NICs -- common on Windows) the beacon can go out the WRONG interface and never reach a
 * peer on the actual Wi-Fi LAN. To be reliable we enumerate every up, non-loopback interface and send to
 * each one's <i>subnet-directed</i> broadcast address (e.g. {@code 192.168.0.255}), plus the limited
 * broadcast as a fallback. The chosen targets are logged once at start so the user can see which networks
 * are being beaconed.
 */
@Slf4j
public final class LanBeacon implements AutoCloseable {

    private final InetAddress explicitTarget;   // non-null => send only here (tests / explicit); null => broadcast mode
    private final int port;
    private final long intervalMillis;
    private final Supplier<LanAnnouncement> supplier;
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;

    /** Explicit single-target beacon (used by tests to beacon to {@code 127.0.0.1}). */
    public LanBeacon(InetAddress target, int port, long intervalMillis, Supplier<LanAnnouncement> supplier) {
        this(requireTarget(target), port, intervalMillis, supplier, false);
    }

    private LanBeacon(InetAddress explicitTarget, int port, long intervalMillis,
                      Supplier<LanAnnouncement> supplier, boolean broadcastMode) {
        this.explicitTarget = broadcastMode ? null : explicitTarget;
        this.port = port;
        this.intervalMillis = intervalMillis;
        this.supplier = supplier;
    }

    /** A beacon broadcasting to every LAN segment the host is attached to, on the well-known discovery port. */
    public static LanBeacon broadcast(long intervalMillis, Supplier<LanAnnouncement> supplier) {
        return new LanBeacon(null, LanDiscovery.DISCOVERY_PORT, intervalMillis, supplier, true);
    }

    /** Opens the UDP socket and starts the periodic beacon thread. */
    public void start() throws SocketException {
        socket = new DatagramSocket();
        socket.setBroadcast(true);
        running = true;
        if (explicitTarget == null) {
            List<InetAddress> targets = broadcastTargets();
            log.info("LAN beacon starting on port {} -> {} target(s): {}", port, targets.size(), targets);
            if (targets.size() == 1) {
                log.warn("LAN beacon found no subnet-directed broadcast address (only the global fallback). "
                        + "If LAN discovery fails, check the host is on Wi-Fi/Ethernet (not just a VPN/virtual NIC) "
                        + "and that UDP {} is allowed through the firewall.", LanDiscovery.DISCOVERY_PORT);
            }
        } else {
            log.info("LAN beacon starting on port {} -> explicit target {}", port, explicitTarget);
        }
        thread = new Thread(this::beaconLoop, "lan-beacon");
        thread.setDaemon(true);
        thread.start();
    }

    private void beaconLoop() {
        long ticks = 0;
        while (running) {
            try {
                int sent = sendOnce();
                // Throttled heartbeat so the user can confirm the host is actively beaconing without log spam.
                if (ticks % 10 == 0) {
                    log.debug("LAN beacon tick {} (sent {} datagram(s) for match {})",
                            ticks, sent, safeMatchCode());
                }
                ticks++;
                Thread.sleep(intervalMillis);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                if (running) {
                    log.debug("Beacon send failed: {}", e.toString());
                }
            }
        }
    }

    /**
     * Sends a single beacon immediately (the unit of the periodic loop; callable after {@link #start}).
     * Returns the number of datagrams actually sent (one per resolved target in broadcast mode).
     */
    public int sendOnce() throws IOException {
        byte[] body = supplier.get().toBytes();
        if (explicitTarget != null) {
            socket.send(new DatagramPacket(body, body.length, explicitTarget, port));
            return 1;
        }
        int sent = 0;
        for (InetAddress target : broadcastTargets()) {
            try {
                socket.send(new DatagramPacket(body, body.length, target, port));
                sent++;
            } catch (IOException e) {
                log.debug("Beacon to {} failed: {}", target, e.toString());
            }
        }
        if (sent == 0) {
            throw new IOException("no broadcast target reachable");
        }
        return sent;
    }

    /**
     * Every IPv4 subnet-directed broadcast address across the host's up, non-loopback interfaces, plus the
     * limited broadcast {@code 255.255.255.255} as a fallback. Recomputed each tick so hot-plugging a NIC
     * (or connecting Wi-Fi after launch) is picked up without restarting the host.
     */
    private List<InetAddress> broadcastTargets() {
        List<InetAddress> targets = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast(); // non-null only for IPv4 with a broadcast addr
                    if (broadcast != null && !targets.contains(broadcast)) {
                        targets.add(broadcast);
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("Could not enumerate network interfaces for broadcast: {}", e.toString());
        }
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (UnknownHostException _) {
            // 255.255.255.255 is always resolvable; this is unreachable in practice.
        }
        return targets;
    }

    private String safeMatchCode() {
        try {
            return supplier.get().matchCode();
        } catch (RuntimeException _) {
            return "?";
        }
    }

    private static InetAddress requireTarget(InetAddress target) {
        if (target == null) {
            throw new IllegalArgumentException("explicit beacon target must not be null");
        }
        return target;
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }
}
