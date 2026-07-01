// src/main/java/pl/mzebrows/shoots/net/OnlineHost.java
package pl.mzebrows.shoots.net;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.world.PlayWorld;

import pl.mzebrows.shoots.world.PlayWorld.AimInput;

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

    /** Neutral input fed for a dropped player's slot so the lockstep gate never stalls on them (#4). */
    private static final TickInput NEUTRAL = new TickInput(AimInput.NONE, false);

    private final PlayWorld world;
    private final TcpServer server;
    private final LockstepCoordinator coordinator;
    private final RoundFlow flow;
    private final int stepsPerFrame;
    private long lastReleasedFrame = -1;

    /** Player slots whose client has dropped mid-match; the host plays them idle so others can continue. */
    private final boolean[] leftSlots;

    private final java.util.Map<Long, Long> ownHashByFrame = new java.util.HashMap<>();
    private int desyncCount;
    private long lastDesyncFrame = -1;

    /** Slot of the player who paused the match, or {@code -1} when running (the host is the pause authority). */
    private int pausedBy = -1;

    /** True once the round timer has expired: the host neutralises the input in every frame it releases so
     *  firing (and aiming) stops on every peer during the disc-settle window, exactly like the offline
     *  keyboard-disable. Reset when the next round's play phase begins (see {@link #setFireDisabled}). */
    private boolean fireDisabled;

    public OnlineHost(PlayWorld world, TcpServer server, int stepsPerFrame, int inputDelayFrames) {
        this.world = world;
        this.server = server;
        this.coordinator = new LockstepCoordinator(world.playerCount(), inputDelayFrames);
        this.flow = new RoundFlow(GameMode.HOST, new ServerControlChannel(server));
        this.stepsPerFrame = stepsPerFrame;
        this.leftSlots = new boolean[world.playerCount()];
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
            } else if (inbound.message() instanceof NetMessage.Pause pause) {
                // A client asked to pause/resume; the host (authority) records it and re-broadcasts to all,
                // tagging it with the AUTHORITATIVE source slot so a client can't pause "as" someone else (#3).
                pausedBy = pause.paused() ? inbound.slot() : -1;
                server.broadcast(new NetMessage.Pause(inbound.slot(), pause.paused()));
            }
        }
    }

    /** Host-initiated pause/resume: records it and broadcasts to every client (the host is slot 0). */
    public void setLocalPaused(boolean paused) {
        pausedBy = paused ? TcpServer.HOST_SLOT : -1;
        server.broadcast(new NetMessage.Pause(TcpServer.HOST_SLOT, paused));
    }

    /** Slot of the player who paused, or {@code -1} when the match is running. */
    public int pausedBy() {
        return pausedBy;
    }

    /**
     * Freezes ({@code true}) or unfreezes ({@code false}) player input in the frames this host releases, for
     * the round-end disc-settle window. While frozen, every released frame is neutralised before broadcast,
     * so firing and aiming stop identically on every peer -- the online equivalent of the offline
     * keyboard-disable. Discs already in flight are unaffected and finish settling as usual.
     */
    public void setFireDisabled(boolean disabled) {
        this.fireDisabled = disabled;
    }

    /**
     * If every slot has reported the next command frame, releases it: broadcasts it to all clients, marks
     * it as the last released frame, and returns it WITHOUT stepping the host world. The caller advances
     * the sim one sub-step per tick via {@link LockstepApplier#applyStep} (so the host renders every
     * sub-step while the network only syncs once per command frame), then calls {@link #recordFrameHash}
     * once all the frame's sub-steps are applied. Returns {@code null} while still waiting on a slot.
     */
    public InputFrame tryReleaseFrame() {
        coverAbsentSlots();
        InputFrame frame = coordinator.tryRelease();
        if (frame == null) {
            return null;
        }
        if (fireDisabled) {
            frame = neutralized(frame); // round time is up: freeze all input so no new discs are fired
        }
        server.broadcast(new NetMessage.Frame(frame.frame(), frame.bySlot()));
        lastReleasedFrame = frame.frame();
        return frame;
    }

    /** A copy of {@code frame} with every slot reset to neutral input (no aim, no fire), used to freeze
     *  player input across all peers once the round timer has expired. */
    private static InputFrame neutralized(InputFrame frame) {
        TickInput[] slots = new TickInput[frame.slots()];
        java.util.Arrays.fill(slots, NEUTRAL);
        return new InputFrame(frame.frame(), slots);
    }

    /** Records the host world's hash for {@code frame} (all its sub-steps applied) for the desync check. */
    public void recordFrameHash(long frame) {
        recordHash(frame);
    }

    /**
     * Releases the next command frame AND fully applies it (all {@code stepsPerFrame} sub-steps) to the
     * host world in one call, returning it or {@code null}. Convenience for direct-drive callers and tests;
     * the live session drives {@link #tryReleaseFrame} plus per-tick {@link LockstepApplier#applyStep}
     * instead, so it can render every sub-step.
     */
    public InputFrame tryAdvance() {
        InputFrame frame = tryReleaseFrame();
        if (frame == null) {
            return null;
        }
        LockstepApplier.apply(world, frame, stepsPerFrame);
        recordHash(frame.frame());
        return frame;
    }

    /**
     * Submits neutral input for the frame currently gating release on behalf of any slot whose client has
     * dropped, so a mid-match disconnect can no longer freeze the whole match (#4). The departed player
     * simply stops acting (idle) from every peer's point of view; the world stays in sync because the host
     * broadcasts the same neutral-filled frame to the remaining clients.
     */
    private void coverAbsentSlots() {
        long gating = coordinator.nextFrameToRelease();
        for (int slot = TcpServer.HOST_SLOT + 1; slot < world.playerCount(); slot++) {
            if (!server.isSlotConnected(slot)) {
                if (!leftSlots[slot]) {
                    leftSlots[slot] = true;
                    log.info("Player slot {} left mid-match; continuing with that player idle", slot);
                }
                coordinator.submit(slot, gating, NEUTRAL);
            }
        }
    }

    /** Player slots that dropped mid-match (now played idle); a defensive copy. */
    public boolean[] leftSlots() {
        return leftSlots.clone();
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
