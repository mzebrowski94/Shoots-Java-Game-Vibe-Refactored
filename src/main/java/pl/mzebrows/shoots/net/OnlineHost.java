// src/main/java/pl/mzebrows/shoots/net/OnlineHost.java
package pl.mzebrows.shoots.net;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Host side of an online match (listen server). Aggregates every player's input for a command frame via
 * a {@link LockstepCoordinator}, then broadcasts the assembled {@link InputFrame} to all clients and
 * advances its OWN world with the same {@link LockstepApplier} -- so every peer simulates identical
 * frames. Round-flow transitions are decided here and broadcast as {@code CONTROL} (see {@link RoundFlow}
 * HOST mode + {@link ServerControlChannel}).
 *
 * <p>Step-driven and AWT-free: the game loop (or a test) calls {@link #submitLocalInput},
 * {@link #pumpInbound} and {@link #tryAdvance} once per command frame; there is no internal thread here
 * beyond the transport's own reader threads. See {@code OnlineMode.md} (cluster F).
 */
@Slf4j
public final class OnlineHost {

    private final PlayWorld world;
    private final TcpServer server;
    private final LockstepCoordinator coordinator;
    private final RoundFlow flow;
    private final int stepsPerFrame;
    private long lastReleasedFrame = -1;

    private final java.util.Map<Long, Long> ownHashByFrame = new java.util.HashMap<>();
    private int desyncCount;
    private long lastDesyncFrame = -1;

    public OnlineHost(PlayWorld world, TcpServer server, int stepsPerFrame, int inputDelayFrames) {
        this.world = world;
        this.server = server;
        this.coordinator = new LockstepCoordinator(world.playerCount(), inputDelayFrames);
        this.flow = new RoundFlow(GameMode.HOST, new ServerControlChannel(server));
        this.stepsPerFrame = stepsPerFrame;
    }

    /** Records the host's own input (slot {@link TcpServer#HOST_SLOT}) for a command frame. */
    public void submitLocalInput(long frame, TickInput input) {
        coordinator.submit(TcpServer.HOST_SLOT, frame, input);
    }

    /** Drains client {@code INPUT} messages into the coordinator, tagged by the slot they arrived from. */
    public void pumpInbound() {
        TcpServer.Inbound inbound;
        while ((inbound = server.poll()) != null) {
            if (inbound.message() instanceof NetMessage.Input msg) {
                coordinator.submit(inbound.slot(), msg.frame(), msg.input());
            } else if (inbound.message() instanceof NetMessage.Hash hash) {
                checkHash(inbound.slot(), hash);
            }
        }
    }

    /**
     * If every slot has reported the next command frame, releases it: broadcasts it to all clients,
     * applies it to the host world, and returns it. Returns {@code null} while still waiting on a slot
     * (the lockstep stall).
     */
    public InputFrame tryAdvance() {
        InputFrame frame = coordinator.tryRelease();
        if (frame == null) {
            return null;
        }
        server.broadcast(new NetMessage.Frame(frame.frame(), frame.bySlot()));
        LockstepApplier.apply(world, frame, stepsPerFrame);
        lastReleasedFrame = frame.frame();
        recordHash(frame.frame());
        return frame;
    }

    private void recordHash(long frame) {
        ownHashByFrame.put(frame, WorldHash.of(world));
        ownHashByFrame.keySet().removeIf(f -> f < frame - 256);
    }

    private void checkHash(int slot, NetMessage.Hash hash) {
        Long own = ownHashByFrame.get(hash.frame());
        if (own != null && own != hash.hash()) {
            desyncCount++;
            lastDesyncFrame = hash.frame();
            log.error("DESYNC detected: slot {} hash {} != host {} at frame {}",
                    slot, hash.hash(), own, hash.frame());
        }
    }

    /** Number of client hash mismatches detected so far (0 = peers in sync). */
    public int desyncCount() {
        return desyncCount;
    }

    /** Frame of the most recent detected desync, or {@code -1} if none. */
    public long lastDesyncFrame() {
        return lastDesyncFrame;
    }

    /** Host-authoritative round flow; driving a transition here broadcasts the matching {@code CONTROL}. */
    public RoundFlow flow() {
        return flow;
    }

    /** Frame number of the last released/broadcast command frame ({@code -1} before the first). */
    public long lastReleasedFrame() {
        return lastReleasedFrame;
    }

    public PlayWorld world() {
        return world;
    }
}
