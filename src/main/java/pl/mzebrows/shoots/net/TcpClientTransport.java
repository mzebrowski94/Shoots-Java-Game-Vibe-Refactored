// src/main/java/pl/mzebrows/shoots/net/TcpClientTransport.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

/**
 * Client side of the listen-server connection: opens a socket to the host, sends {@code JOIN}, and then
 * exchanges messages (sends {@code INPUT}; receives {@code WELCOME}/{@code FRAME}/{@code CONTROL}).
 * Internet joins use this with a manual {@code IP:port}; LAN joins use a discovered host's address.
 * See OnlineMode.md (F3/F4).
 */
@Slf4j
public final class TcpClientTransport implements AutoCloseable {

    /** Wire protocol version; bump on any incompatible message change. */
    public static final int PROTOCOL_VERSION = 1;

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final TcpConnection connection;
    private NetMessage.Welcome welcome;

    private TcpClientTransport(TcpConnection connection) {
        this.connection = connection;
    }

    /** Connects to {@code host:port}, starts reading, and sends the JOIN with {@code playerName}. */
    public static TcpClientTransport connect(String host, int port, String playerName) throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        var conn = new TcpConnection(socket);
        conn.startReader("net-client-reader");
        var transport = new TcpClientTransport(conn);
        conn.send(new NetMessage.Join(playerName, PROTOCOL_VERSION));
        return transport;
    }

    /** Blocks up to {@code timeoutMs} for the host's WELCOME and returns it (also cached). */
    public NetMessage.Welcome awaitWelcome(long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            NetMessage message = connection.poll();
            if (message instanceof NetMessage.Welcome w) {
                welcome = w;
                return w;
            }
            if (message != null) {
                log.debug("Ignoring message received before WELCOME: {}", message);
            }
            sleepBriefly();
        }
        throw new IllegalStateException("No WELCOME within " + timeoutMs + " ms");
    }

    /** The WELCOME received at join, or {@code null} if not yet received. */
    public NetMessage.Welcome welcome() {
        return welcome;
    }

    public void send(NetMessage message) {
        connection.send(message);
    }

    public NetMessage poll() {
        return connection.poll();
    }

    public boolean isOpen() {
        return connection.isOpen();
    }

    @Override
    public void close() {
        connection.close();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
