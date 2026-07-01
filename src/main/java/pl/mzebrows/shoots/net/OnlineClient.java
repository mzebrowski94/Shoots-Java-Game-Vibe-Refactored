// src/main/java/pl/mzebrows/shoots/net/OnlineClient.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Client side of an online match. Sends its local input to the host and applies the AUTHORITATIVE
 * {@link InputFrame}s the host broadcasts back (the client does no aggregation -- the host is the
 * authority), plus the host's round-flow {@code CONTROL}s, which drive a CLIENT-mode {@link RoundFlow}.
 * Applying the host's frames with the same {@link LockstepApplier} keeps the client world identical to
 * the host's.
 *
 * <p>Step-driven and AWT-free: the game loop (or a test) calls {@link #sendLocalInput} and {@link #pump}
 * once per command frame. See {@code OnlineMode.md} (cluster F).
 */
public final class OnlineClient {

    private final PlayWorld world;
    private final TcpClientTransport transport;
    private final LoopbackControlChannel inboundControl = new LoopbackControlChannel();
    private final RoundFlow flow;
    private final int stepsPerFrame;
    private long lastAppliedFrame = -1;

    /** Slot of the player who paused (from the host's authoritative broadcast), or {@code -1} when running. */
    private int pausedBy = -1;

    public OnlineClient(PlayWorld world, TcpClientTransport transport, int stepsPerFrame) {
        this.world = world;
        this.transport = transport;
        this.flow = new RoundFlow(GameMode.CLIENT, inboundControl);
        this.stepsPerFrame = stepsPerFrame;
    }

    /** Sends this client's input for a command frame to the host. */
    public void sendLocalInput(long frame, TickInput input) {
        transport.send(new NetMessage.Input(frame, input));
    }

    /** This client's current world hash (for the host's desync check). */
    public long worldHash() {
        return WorldHash.of(world);
    }

    /** Sends this client's world hash for {@code frame} to the host (desync detection). */
    public void sendHash(long frame) {
        transport.send(new NetMessage.Hash(frame, WorldHash.of(world)));
    }

    /**
     * Drains round-flow {@code CONTROL}s and pause requests, and returns the NEXT authoritative
     * {@link InputFrame} to apply (or {@code null} if none is queued) WITHOUT stepping the world. At most
     * one frame is returned per call -- further queued frames wait for the next call -- matching the host's
     * one-frame-per-command-frame contract. The caller advances the sim one sub-step per tick via
     * {@link LockstepApplier#applyStep}, so the client renders every sub-step while the host only sends a
     * frame once per command frame. Frames arrive in order over TCP, so they surface in order.
     */
    public InputFrame nextFrame() {
        NetMessage message;
        while ((message = transport.poll()) != null) {
            switch (message) {
                case NetMessage.Frame f -> {
                    lastAppliedFrame = f.frame();
                    return new InputFrame(f.frame(), f.bySlot());
                }
                case NetMessage.Control c -> inboundControl.send(new ControlEvent(c.frame(), c.kind()));
                case NetMessage.Pause p -> pausedBy = p.paused() ? p.slot() : -1;
                default -> { /* WELCOME consumed at connect; JOIN/Input are server-bound */ }
            }
        }
        return null;
    }

    /**
     * Drains controls/pauses and applies AT MOST ONE authoritative frame (all its sub-steps) to the world.
     * Convenience for direct-drive callers and tests; the live session uses {@link #nextFrame} plus per-tick
     * {@link LockstepApplier#applyStep} instead, so it can render every sub-step.
     */
    public void pump() {
        InputFrame frame = nextFrame();
        if (frame != null) {
            LockstepApplier.apply(world, frame, stepsPerFrame);
        }
    }

    /** Sends this client's pause/resume request to the host (the authority that broadcasts it to all). */
    public void sendPause(int slot, boolean paused) {
        transport.send(new NetMessage.Pause(slot, paused));
    }

    /** Slot of the player who paused (per the host's last broadcast), or {@code -1} when running. */
    public int pausedBy() {
        return pausedBy;
    }

    /** Round flow following the host (advance methods consume the queued {@code CONTROL}s). */
    public RoundFlow flow() {
        return flow;
    }

    /** Frame number of the last host frame applied ({@code -1} before the first). */
    public long lastAppliedFrame() {
        return lastAppliedFrame;
    }

    public PlayWorld world() {
        return world;
    }
}
