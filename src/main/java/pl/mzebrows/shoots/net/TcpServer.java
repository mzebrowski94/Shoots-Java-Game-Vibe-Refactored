// src/main/java/pl/mzebrows/shoots/net/TcpServer.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Listen-server TCP endpoint: accepts client connections (thread-per-connection), assigns each a player
 * slot, and sends a {@code WELCOME} carrying the master seed + player count + match code so the joiner
 * can build an identical world. Relays {@link #broadcast}s to every client and aggregates inbound client
 * messages tagged by slot via {@link #poll()}. The host occupies {@link #HOST_SLOT}; clients fill
 * {@code 1..playerCount-1}. See OnlineMode.md (F3).
 */
@Slf4j
public final class TcpServer implements AutoCloseable {

    /** The host's own player slot (the host is also a player in the listen-server model). */
    public static final int HOST_SLOT = 0;

    private static final long JOIN_TIMEOUT_MS = 3000;

    /** A client message tagged with the slot it arrived from. */
    public record Inbound(int slot, NetMessage message) { }

    private record Client(int slot, TcpConnection connection) { }

    private final ServerSocket serverSocket;
    private final int playerCount;
    private final long seed;
    private final String matchCode;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;
    private Thread acceptThread;

    /** Binds the server (use {@code port == 0} for an OS-assigned ephemeral port). */
    public TcpServer(int port, int playerCount, long seed, String matchCode) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.playerCount = playerCount;
        this.seed = seed;
        this.matchCode = matchCode;
    }

    /** The actual bound port (resolves an ephemeral port chosen for {@code port == 0}). */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /** Starts the daemon accept loop. */
    public void start() {
        acceptThread = new Thread(this::acceptLoop, "net-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                handleNewClient(socket);
            } catch (IOException e) {
                if (running) {
                    log.debug("Accept loop stopped: {}", e.toString());
                }
                return;
            }
        }
    }

    private void handleNewClient(Socket socket) throws IOException {
        var conn = new TcpConnection(socket);
        conn.startReader("net-client-reader-" + clients.size());
        NetMessage join = awaitFrame(conn, JOIN_TIMEOUT_MS);
        if (!(join instanceof NetMessage.Join)) {
            log.warn("Client did not JOIN first (got {}); dropping", join);
            conn.close();
            return;
        }
        if (clients.size() + 1 >= playerCount) {
            log.warn("Match full ({} players); rejecting client", playerCount);
            conn.close();
            return;
        }
        int slot = clients.size() + 1; // host occupies slot 0
        conn.send(new NetMessage.Welcome(slot, playerCount, seed, matchCode));
        clients.add(new Client(slot, conn));
        log.info("Client joined as slot {} (match {})", slot, matchCode);
    }

    /** Sends {@code message} to every connected client. */
    public void broadcast(NetMessage message) {
        for (Client c : clients) {
            c.connection().send(message);
        }
    }

    /** Next pending client message (tagged by its slot), or {@code null} if none is pending. */
    public Inbound poll() {
        for (Client c : clients) {
            NetMessage message = c.connection().poll();
            if (message != null) {
                return new Inbound(c.slot(), message);
            }
        }
        return null;
    }

    public int connectedClients() {
        return clients.size();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // best-effort close
        }
        for (Client c : clients) {
            c.connection().close();
        }
    }

    private static NetMessage awaitFrame(TcpConnection conn, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            NetMessage message = conn.poll();
            if (message != null) {
                return message;
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }
}
