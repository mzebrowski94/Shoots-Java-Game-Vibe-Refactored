// src/main/java/pl/mzebrows/shoots/net/ControlChannel.java
package pl.mzebrows.shoots.net;

/**
 * One-way carrier for host-authoritative {@link ControlEvent}s (host {@link #send}s, client
 * {@link #poll}s). Kept deliberately tiny so the transport can be swapped: {@link LoopbackControlChannel}
 * for single-process play/tests now, a TCP-backed implementation in F3.
 */
public interface ControlChannel {

    /** Enqueues an event for delivery (host side). */
    void send(ControlEvent event);

    /** Removes and returns the next event, or {@code null} if none is pending (client side). */
    ControlEvent poll();

    /** Returns the next event without removing it, or {@code null} if none is pending. */
    ControlEvent peek();
}
