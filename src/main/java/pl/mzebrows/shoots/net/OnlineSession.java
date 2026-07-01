// src/main/java/pl/mzebrows/shoots/net/OnlineSession.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * A live online match for ONE peer: it bootstraps the host (server + LAN beacon + {@link OnlineHost}) or
 * a client ({@link TcpClientTransport} + {@link OnlineClient}), builds the shared {@link PlayWorld} from
 * the master seed (the host's own / the client's from {@code WELCOME}), and advances the match one
 * sim sub-step per {@link #advance} call. It is step-driven and AWT-free, so the game loop calls
 * {@link #advance} each tick and renders {@link #world()}; the engine pieces underneath are the
 * fully-tested F1-F5 stack. See OnlineMode.md (F6).
 *
 * <p><b>Cadence:</b> the sim runs at 120 Hz and the network is consulted at ~30 Hz -- one
 * <em>command frame</em> = {@link #STEPS_PER_FRAME} (4) held sim steps, per the original ADR ("run a
 * command frame at ~30 Hz (4 sim steps per frame)"). {@link #advance} is called once per real (120 Hz)
 * game-loop tick and steps the world exactly ONE sub-step of the current command frame each call, only
 * consulting the network at a command-frame boundary (every {@code STEPS_PER_FRAME} calls) to release or
 * receive the next frame. So the sim advances smoothly at the full 120 Hz -- the same pacing as offline --
 * while the network still only syncs ~30x/s. Syncing 4x less often means 4x fewer chances per second for a
 * single jitter spike to cause a stall, and a stall then costs at most one command frame; see
 * {@link #advanceWith}.
 *
 * <p>Lockstep also runs with a small <b>input delay</b> ({@link #INPUT_DELAY_FRAMES}, in COMMAND frames):
 * the input sampled for command frame {@code f} is scheduled for frame {@code f + delay}, and frames
 * {@code [0, delay)} are pre-seeded with neutral input. This keeps every peer's input buffered a few
 * command frames ahead, so a peer advances without waiting a full network round-trip per command frame --
 * the game then runs at real-time speed instead of stalling to the round-trip rate of the slowest peer.
 */
@Slf4j
public final class OnlineSession implements AutoCloseable {

    private static final long WELCOME_TIMEOUT_MS = 5000;

    /**
     * Sim steps held per command frame (120 Hz sim / 30 Hz command frame, per {@code OnlineMode.md}).
     * {@link LockstepApplier} re-applies the SAME held input over all of them, so it is bit-identical to
     * {@code STEPS_PER_FRAME} normal per-tick updates -- see its javadoc.
     */
    static final int STEPS_PER_FRAME = 4;

    /**
     * Command frames of input delay. The local input read at apply-frame {@code f} takes effect at frame
     * {@code f + delay}; frames {@code [0, delay)} use neutral input. This buffer is what hides network
     * round-trip jitter so {@link #advance} returns {@code true} every tick (real-time pacing) instead of
     * stalling the whole match to the round-trip rate of the slowest peer.
     *
     * <p>At {@code STEPS_PER_FRAME} sim steps per command frame (30 command frames/s), {@code delay}
     * frames buy {@code delay / 30 s} of slack: 3 frames ~= 100 ms, matching {@code OnlineMode.md}'s
     * design target (accepted as imperceptible for this aim-and-shoot genre). Before command frames were
     * batched, this same field held sim-tick units (12 frames @ 120 Hz was the equivalent ~100 ms); now
     * that a "frame" is a command frame again, 3 is the right number -- don't reintroduce the old 120 Hz
     * unit by accident when tuning this. Tune further with real latency data per the ADR's open item.
     */
    private static final int INPUT_DELAY_FRAMES = 3;

    /** Neutral "no aim, not shooting" input used to pre-seed the delay window. */
    private static final TickInput NEUTRAL = new TickInput(PlayWorld.AimInput.NONE, false);

    private final GameMode mode;
    private final PlayWorld world;
    private final int localSlot;
    private final String matchCode;

    private final OnlineHost host;          // HOST mode
    private final OnlineClient client;      // CLIENT mode
    private final TcpServer server;         // HOST mode
    private final LanBeacon beacon;         // HOST mode
    private final TcpClientTransport transport; // CLIENT mode

    private long frame;         // command frames FULLY applied so far (advances only at a frame boundary)
    private boolean dispatched; // local input already sent/submitted for the current command frame
    private boolean primed;     // the neutral input for the delay window [0, delay) has been dispatched
    // The command frame currently being applied one sub-step at a time, and how many of its
    // STEPS_PER_FRAME sub-steps are still to run. subStepsRemaining == 0 means "at a command-frame
    // boundary: consult the network for the next frame". Spreading the sub-steps across real ticks is what
    // lets the sim render at the full 120 Hz while the network only syncs once per command frame (~30 Hz).
    private InputFrame heldFrame;
    private int subStepsRemaining;
    // CLIENT: authoritative frames received from the host but not yet consumed -- drained off the transport
    // each tick (so control/pause stay live) and applied one command frame at a time, in arrival order.
    private final ArrayDeque<InputFrame> pendingFrames = new ArrayDeque<>();
    // Whether the last advanceWith that returned false was a genuine network stall (a command-frame
    // boundary reached with no next frame ready). The game loop refunds real time only on a stall.
    private boolean lastStalled;

    private OnlineSession(GameMode mode, PlayWorld world, int localSlot, String matchCode,
                          OnlineHost host, OnlineClient client,
                          TcpServer server, LanBeacon beacon, TcpClientTransport transport) {
        this.mode = mode;
        this.world = world;
        this.localSlot = localSlot;
        this.matchCode = matchCode;
        this.host = host;
        this.client = client;
        this.server = server;
        this.beacon = beacon;
        this.transport = transport;
    }

    // -- bootstrap ----------------------------------------------------------

    /** Starts hosting: builds the world from a fresh seed, opens the server, and beacons on the LAN. */
    public static OnlineSession host(GameConfig base, int playerNumber, int port, String playerName)
            throws IOException {
        long seed = new Random().nextLong();
        String code = MatchCode.generate();
        GameConfig cfg = base.withPlayerNumber(playerNumber).withSeed(seed);
        var world = new PlayWorld(cfg);
        var server = new TcpServer(port, playerNumber, seed, code);
        server.start();
        var beacon = LanBeacon.broadcast(1000,
                () -> new LanAnnouncement(code, playerName, server.port(), playerNumber, true));
        beacon.start();
        var onlineHost = new OnlineHost(world, server, STEPS_PER_FRAME, 0);
        log.info("Hosting match {} on port {} ({} players)", code, server.port(), playerNumber);
        return new OnlineSession(GameMode.HOST, world, TcpServer.HOST_SLOT, code,
                onlineHost, null, server, beacon, null);
    }

    /** Joins a host at {@code host:port}: builds the world from the seed delivered in {@code WELCOME}. */
    public static OnlineSession join(GameConfig base, String host, int port, String playerName)
            throws IOException {
        var transport = TcpClientTransport.connect(host, port, playerName);
        NetMessage.Welcome welcome = transport.awaitWelcome(WELCOME_TIMEOUT_MS);
        GameConfig cfg = base.withPlayerNumber(welcome.playerCount()).withSeed(welcome.seed());
        var world = new PlayWorld(cfg);
        var onlineClient = new OnlineClient(world, transport, STEPS_PER_FRAME);
        log.info("Joined match {} as slot {} ({} players)",
                welcome.matchCode(), welcome.slot(), welcome.playerCount());
        return new OnlineSession(GameMode.CLIENT, world, welcome.slot(), welcome.matchCode(),
                null, onlineClient, null, null, transport);
    }

    /**
     * Wraps an already-open host lobby into a live match: the {@code server} + {@code beacon} were opened
     * during the waiting room ({@link OnlineLobby}) and keep their connected clients; only the
     * {@link OnlineHost} (world aggregator) is created here. Called when the host presses START.
     */
    static OnlineSession startedHost(PlayWorld world, TcpServer server, LanBeacon beacon, String matchCode) {
        var onlineHost = new OnlineHost(world, server, STEPS_PER_FRAME, 0);
        return new OnlineSession(GameMode.HOST, world, TcpServer.HOST_SLOT, matchCode,
                onlineHost, null, server, beacon, null);
    }

    /**
     * Wraps an already-connected client lobby into a live match: the {@code transport} stays connected from
     * the waiting room and the world is built from the host's START seed; only the {@link OnlineClient} is
     * created here. {@code playerSlot} is this peer's final 0-based player id.
     */
    static OnlineSession startedClient(PlayWorld world, TcpClientTransport transport, int playerSlot,
                                       String matchCode) {
        var onlineClient = new OnlineClient(world, transport, STEPS_PER_FRAME);
        return new OnlineSession(GameMode.CLIENT, world, playerSlot, matchCode,
                null, onlineClient, null, null, transport);
    }

    // -- per-tick drive -----------------------------------------------------

    /** Advances one command frame from the local keyboard; returns whether the world actually stepped. */
    public boolean advance(InputBridge input) {
        return advanceWith(localInput(input));
    }

    /**
     * Advances one REAL (120 Hz) tick from an explicit local input (used by tests); returns whether the
     * world stepped this call (it does every tick except a genuine network stall).
     *
     * <p><b>Cadence.</b> A command frame authorises {@link #STEPS_PER_FRAME} sim sub-steps of the same held
     * input. Rather than apply them all in one call (which made online motion update in 30 Hz bursts), this
     * applies exactly ONE sub-step per call and only consults the network at a command-frame boundary
     * ({@code subStepsRemaining == 0}) to release/receive the next frame. So the sim advances one step per
     * tick -- identical pacing to the offline path, smooth at the full 120 Hz -- while the network still
     * only syncs once per command frame (~30 Hz), preserving the jitter tolerance that keeps one slow peer
     * from stalling the others.
     *
     * <p>The only tick that does not step is a genuine stall: a boundary reached with no next frame ready
     * ({@link #lastAdvanceStalled()} true). It returns {@code false} so the game loop refunds the tick's
     * real time and freezes with the sim until the peer's input arrives.
     */
    boolean advanceWith(TickInput local) {
        prime();
        lastStalled = false;
        if (subStepsRemaining == 0) {
            // At a command-frame boundary: schedule our own (delayed) input once, then fetch the next frame.
            if (!dispatched) {
                dispatchLocal(frame + INPUT_DELAY_FRAMES, local);
                dispatched = true;
            }
            InputFrame next = nextCommandFrame();
            if (next == null) {
                lastStalled = true; // no frame ready -> real lockstep stall; the loop refunds this tick
                return false;
            }
            heldFrame = next;
            subStepsRemaining = STEPS_PER_FRAME;
        }
        LockstepApplier.applyStep(world, heldFrame);
        if (--subStepsRemaining == 0) {
            // Command frame fully applied: hash it at the boundary (desync check) and count it complete.
            if (mode == GameMode.HOST) {
                host.recordFrameHash(heldFrame.frame());
            }
            frame++;
            dispatched = false;
        }
        return true;
    }

    /**
     * Whether the last {@link #advanceWith} that returned {@code false} was a genuine network stall -- a
     * command-frame boundary reached with no next frame ready. Every other tick steps the sim and consumes
     * its real time; only a stall refunds it (freezing wall-clock progress with the frozen sim), so online
     * wall-clock speed matches offline. See {@link GameLoop}/{@code PlayingState}.
     */
    public boolean lastAdvanceStalled() {
        return lastStalled;
    }

    /**
     * The next command frame to apply, or {@code null} if it is not ready yet. HOST: drains client input
     * and releases the frame once every slot has reported it (broadcasting it to the clients). CLIENT:
     * returns the next host-broadcast frame from the pending queue (kept filled off the transport).
     */
    private InputFrame nextCommandFrame() {
        if (mode == GameMode.HOST) {
            host.pumpInbound();
            return host.tryReleaseFrame();
        }
        drainClientFrames();
        return pendingFrames.pollFirst();
    }

    /** Drains the client transport: applies host {@code CONTROL}s/pauses and queues authoritative frames. */
    private void drainClientFrames() {
        InputFrame f;
        while ((f = client.nextFrame()) != null) {
            pendingFrames.addLast(f);
        }
    }

    /** Sends/submits this peer's input for command frame {@code frameNumber}. */
    private void dispatchLocal(long frameNumber, TickInput local) {
        if (mode == GameMode.HOST) {
            host.submitLocalInput(frameNumber, local);
        } else {
            client.sendLocalInput(frameNumber, local);
        }
    }

    /**
     * Dispatches this peer's neutral input for the delay window {@code [0, delay)} once, so those leading
     * frames complete immediately and the gate can start releasing while real (delayed) inputs flow in.
     */
    private void prime() {
        if (primed) {
            return;
        }
        for (long f = 0; f < INPUT_DELAY_FRAMES; f++) {
            if (mode == GameMode.HOST) {
                host.submitLocalInput(f, NEUTRAL);
            } else {
                client.sendLocalInput(f, NEUTRAL);
            }
        }
        primed = true;
    }

    /** Builds the local player's {@link TickInput} from the P1 keys, mapped onto this peer's own slot. */
    private TickInput localInput(InputBridge input) {
        boolean left = input.isHeld(GameAction.P1_ROTATE_LEFT);
        boolean right = input.isHeld(GameAction.P1_ROTATE_RIGHT);
        PlayWorld.AimInput aim = left ? PlayWorld.AimInput.LEFT
                : right ? PlayWorld.AimInput.RIGHT : PlayWorld.AimInput.NONE;
        if (PlayWorld.aimKeysMirrored(localSlot)) {
            aim = switch (aim) {
                case LEFT -> PlayWorld.AimInput.RIGHT;
                case RIGHT -> PlayWorld.AimInput.LEFT;
                case NONE -> PlayWorld.AimInput.NONE;
            };
        }
        return new TickInput(aim, input.isHeld(GameAction.P1_SHOOT));
    }

    // -- queries ------------------------------------------------------------

    /**
     * Receives pending network messages WITHOUT advancing a command frame. Call this in the non-playing
     * phases (round begin / end) so a CLIENT still receives the host's phase {@code CONTROL}s and the host
     * keeps draining client traffic, even though no input is exchanged or step taken there.
     */
    public void pump() {
        if (mode == GameMode.HOST) {
            host.pumpInbound();
        } else {
            // Keep control/pause state live and BUFFER any frames that arrived, without stepping here --
            // advanceWith applies them one sub-step per tick. Draining (not applying) is what stops a
            // frozen or non-playing tick from silently advancing the world.
            drainClientFrames();
        }
    }

    /**
     * Requests a match-wide pause/resume from this peer (#3). The HOST broadcasts it directly; a CLIENT asks
     * the host, which re-broadcasts authoritatively. Every peer then observes it via {@link #pausedBy()}.
     */
    public void requestPause(boolean paused) {
        if (mode == GameMode.HOST) {
            host.setLocalPaused(paused);
        } else {
            client.sendPause(localSlot, paused);
        }
    }

    /**
     * HOST: freeze ({@code false}) or unfreeze ({@code true}) player input in the authoritative frames the
     * host broadcasts, mirroring the offline keyboard-disable so firing (and aiming) stops on every peer for
     * the round-end disc-settle window. No-op on a CLIENT -- its input is suppressed at the host. Called with
     * {@code false} when the round timer expires and {@code true} when a new round's play phase begins.
     */
    public void setFireEnabled(boolean enabled) {
        if (mode == GameMode.HOST) {
            host.setFireDisabled(!enabled);
        }
    }

    /** Slot of the player who paused the match, or {@code -1} when running (same value on every peer). */
    public int pausedBy() {
        return mode == GameMode.HOST ? host.pausedBy() : client.pausedBy();
    }

    /** Player slots that dropped mid-match and are now played idle (#4); empty for a client. */
    public boolean[] leftSlots() {
        return host != null ? host.leftSlots() : new boolean[world.playerCount()];
    }

    /** The round flow this peer follows (HOST decides + broadcasts, CLIENT follows); for {@code PlayingState}. */
    public RoundFlow flow() {
        return mode == GameMode.HOST ? host.flow() : client.flow();
    }

    public GameMode mode() {
        return mode;
    }

    public PlayWorld world() {
        return world;
    }

    public int localSlot() {
        return localSlot;
    }

    public String matchCode() {
        return matchCode;
    }

    /** Current command frame (the next frame awaiting completion). */
    public long frame() {
        return frame;
    }

    /** The host's bound port (host mode); -1 for a client. */
    public int port() {
        return server != null ? server.port() : -1;
    }

    /** Detected desync count (host mode; 0 for a client, which never compares). */
    public int desyncCount() {
        return host != null ? host.desyncCount() : 0;
    }

    public boolean isConnected() {
        return mode == GameMode.HOST ? server.connectedClients() > 0 : transport.isOpen();
    }

    @Override
    public void close() {
        if (beacon != null) {
            beacon.close();
        }
        if (server != null) {
            server.close();
        }
        if (transport != null) {
            transport.close();
        }
    }
}
