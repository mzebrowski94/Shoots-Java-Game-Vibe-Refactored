// src/main/java/pl/mzebrows/shoots/net/LoopbackControlChannel.java
package pl.mzebrows.shoots.net;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * In-memory {@link ControlChannel} for single-process host+client play and unit tests: events sent by
 * the host are delivered to the client in FIFO order. No sockets, no serialization -- this is the
 * loopback stand-in that F3's TCP transport will replace while keeping the same interface.
 */
public final class LoopbackControlChannel implements ControlChannel {

    private final Deque<ControlEvent> queue = new ArrayDeque<>();

    @Override
    public void send(ControlEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        queue.addLast(event);
    }

    @Override
    public ControlEvent poll() {
        return queue.pollFirst();
    }

    @Override
    public ControlEvent peek() {
        return queue.peekFirst();
    }

    /** Number of undelivered events (for tests/diagnostics). */
    public int pending() {
        return queue.size();
    }
}
