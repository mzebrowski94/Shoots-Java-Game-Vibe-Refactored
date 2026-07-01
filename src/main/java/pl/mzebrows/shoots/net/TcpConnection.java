// src/main/java/pl/mzebrows/shoots/net/TcpConnection.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * One TCP link wrapped for framed {@link NetMessage} exchange: a daemon reader thread decodes inbound
 * frames into a lock-free queue (drained via {@link #poll()} by the game/loop thread), and a SEPARATE
 * daemon writer thread does the actual blocking {@code OutputStream} write, draining a bounded outbound
 * queue that {@link #send} only enqueues onto. Blocking I/O, thread-per-connection -- simple and
 * debuggable for the <=4-peer star (see OnlineMode.md, F3) -- but a per-connection writer thread is
 * essential: a slow/lossy peer's TCP receive window can make the write itself block for a long time, and
 * without this indirection that block would happen on whichever thread calls {@link #send} (the host's
 * own game-loop thread via {@code TcpServer.broadcast}), freezing the simulation for EVERY peer, not just
 * the slow one.
 */
@Slf4j
final class TcpConnection implements AutoCloseable {

    /** Outbound backlog before a peer is declared unresponsive and dropped (see {@link #send}). */
    private static final int OUTBOUND_CAPACITY = 512;

    /** Bound on how long a graceful {@link #close()} waits for the writer to flush queued messages. */
    private static final long CLOSE_DRAIN_TIMEOUT_MS = 500;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Queue<NetMessage> inbound = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<NetMessage> outbound = new LinkedBlockingQueue<>(OUTBOUND_CAPACITY);
    private volatile boolean open = true;
    private Thread reader;
    private Thread writer;

    TcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** Starts the daemon reader + writer threads that service the inbound/outbound queues until closed. */
    void startReader(String name) {
        reader = new Thread(this::readLoop, name);
        reader.setDaemon(true);
        reader.start();
        writer = new Thread(this::writeLoop, name + "-writer");
        writer.setDaemon(true);
        writer.start();
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

    /** Drains {@link #outbound} and performs the actual (possibly slow/blocking) socket writes, off the
     *  caller's thread. Keeps writing after {@code open} flips false so a message enqueued just before
     *  {@link #close()} (e.g. a match-over CONTROL) still gets a chance to reach the wire. */
    private void writeLoop() {
        try {
            while (open) {
                NetMessage message = outbound.poll(50, TimeUnit.MILLISECONDS);
                if (message != null) {
                    MessageCodec.writeFrame(out, message);
                }
            }
            NetMessage message;
            while ((message = outbound.poll()) != null) {
                MessageCodec.writeFrame(out, message);
            }
        } catch (IOException e) {
            log.debug("Writer stopped on {}: {}", name(), e.toString());
            open = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enqueues one framed message for the writer thread; never blocks and never performs I/O itself, so a
     * congested peer cannot stall the caller. If the outbound backlog is full the peer is treated as
     * unresponsive and the connection is dropped immediately (matching the existing "client disconnects
     * mid-match" handling, which keeps the remaining peers running via neutral input -- see
     * {@code OnlineHost#coverAbsentSlots}).
     */
    void send(NetMessage message) {
        if (!open) {
            return;
        }
        if (!outbound.offer(message)) {
            log.warn("Outbound backlog full on {} ({} pending); dropping unresponsive peer",
                    name(), OUTBOUND_CAPACITY);
            closeAbruptly();
        }
    }

    /** Next decoded inbound message, or {@code null} if none pending. */
    NetMessage poll() {
        return inbound.poll();
    }

    boolean isOpen() {
        return open && !socket.isClosed();
    }

    /**
     * Graceful close: gives the writer thread a short bound to flush anything already queued (so a
     * final broadcast like MATCH_OVER, sent just before teardown, still reaches the wire) before the
     * socket is actually torn down. Bounded, so a genuinely dead peer can't hang the caller.
     */
    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        if (writer != null && writer.isAlive()) {
            try {
                writer.join(CLOSE_DRAIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeSocket();
    }

    /** Immediate close, no drain -- used once a peer is already deemed unresponsive (full outbound queue). */
    private void closeAbruptly() {
        open = false;
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException _) {
            // best-effort close
        }
    }

    private String name() {
        return reader != null ? reader.getName() : "tcp-connection";
    }
}
