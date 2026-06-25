// src/main/java/pl/mzebrows/shoots/net/TcpConnection.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;

/**
 * One TCP link wrapped for framed {@link NetMessage} exchange: a daemon reader thread decodes inbound
 * frames into a lock-free queue (drained via {@link #poll()} by the game/loop thread), while
 * {@link #send} writes outbound frames under a lock so concurrent senders never interleave. Blocking
 * I/O, thread-per-connection -- simple and debuggable for the <=4-peer star (see OnlineMode.md, F3).
 */
@Slf4j
final class TcpConnection implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Queue<NetMessage> inbound = new ConcurrentLinkedQueue<>();
    private volatile boolean open = true;
    private Thread reader;

    TcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** Starts the daemon reader thread that fills the inbound queue until the stream closes. */
    void startReader(String name) {
        reader = new Thread(this::readLoop, name);
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            NetMessage message;
            while (open && (message = MessageCodec.readFrame(in)) != null) {
                inbound.add(message);
            }
        } catch (IOException e) {
            if (open) {
                log.debug("Reader stopped on {}: {}", name(), e.toString());
            }
        } finally {
            open = false;
        }
    }

    /** Writes one framed message; on I/O failure the connection is marked closed (no throw). */
    synchronized void send(NetMessage message) {
        if (!open) {
            return;
        }
        try {
            MessageCodec.writeFrame(out, message);
        } catch (IOException e) {
            open = false;
            log.debug("Send failed on {}: {}", name(), e.toString());
        }
    }

    /** Next decoded inbound message, or {@code null} if none pending. */
    NetMessage poll() {
        return inbound.poll();
    }

    boolean isOpen() {
        return open && !socket.isClosed();
    }

    @Override
    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort close
        }
    }

    private String name() {
        return reader != null ? reader.getName() : "tcp-connection";
    }
}
