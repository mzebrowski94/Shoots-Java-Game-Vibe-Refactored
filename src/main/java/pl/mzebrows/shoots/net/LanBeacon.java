// src/main/java/pl/mzebrows/shoots/net/LanBeacon.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Host-side LAN beacon: periodically sends a {@link LanAnnouncement} over UDP so clients can
 * auto-discover the match (see OnlineMode.md, F4). Production uses {@link #broadcast} (segment broadcast
 * on {@link LanDiscovery#DISCOVERY_PORT}); the explicit-target constructor lets tests beacon to
 * {@code 127.0.0.1}. The announcement is re-read from the supplier each tick, so a changing player count
 * / joinable flag is reflected automatically.
 */
@Slf4j
public final class LanBeacon implements AutoCloseable {

    private final InetAddress target;
    private final int port;
    private final long intervalMillis;
    private final Supplier<LanAnnouncement> supplier;
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;

    public LanBeacon(InetAddress target, int port, long intervalMillis, Supplier<LanAnnouncement> supplier) {
        this.target = target;
        this.port = port;
        this.intervalMillis = intervalMillis;
        this.supplier = supplier;
    }

    /** A beacon broadcasting to the whole LAN segment on the well-known discovery port. */
    public static LanBeacon broadcast(long intervalMillis, Supplier<LanAnnouncement> supplier)
            throws UnknownHostException {
        return new LanBeacon(InetAddress.getByName("255.255.255.255"),
                LanDiscovery.DISCOVERY_PORT, intervalMillis, supplier);
    }

    /** Opens the UDP socket and starts the periodic beacon thread. */
    public void start() throws SocketException {
        socket = new DatagramSocket();
        socket.setBroadcast(true);
        running = true;
        thread = new Thread(this::beaconLoop, "lan-beacon");
        thread.setDaemon(true);
        thread.start();
    }

    private void beaconLoop() {
        while (running) {
            try {
                sendOnce();
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                if (running) {
                    log.debug("Beacon send failed: {}", e.toString());
                }
            }
        }
    }

    /** Sends a single beacon immediately (the unit of the periodic loop; callable after {@link #start}). */
    public void sendOnce() throws IOException {
        byte[] body = supplier.get().toBytes();
        socket.send(new DatagramPacket(body, body.length, target, port));
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
