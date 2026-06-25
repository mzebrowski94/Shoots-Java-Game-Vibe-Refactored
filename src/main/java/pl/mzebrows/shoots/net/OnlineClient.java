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
     * Applies everything the host has broadcast since the last call: authoritative {@link InputFrame}s
     * (advance the world) and round-flow {@code CONTROL}s (queued for the CLIENT {@link RoundFlow} to
     * follow). Frames arrive in order over TCP, so they apply in order.
     */
    public void pump() {
        NetMessage message;
        while ((message = transport.poll()) != null) {
            switch (message) {
                case NetMessage.Frame f -> {
                    LockstepApplier.apply(world, new InputFrame(f.frame(), f.bySlot()), stepsPerFrame);
                    lastAppliedFrame = f.frame();
                }
                case NetMessage.Control c -> inboundControl.send(new ControlEvent(c.frame(), c.kind()));
                default -> { /* WELCOME consumed at connect; JOIN/Input are server-bound */ }
            }
        }
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
