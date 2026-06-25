// src/main/java/pl/mzebrows/shoots/net/OnlineSession.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
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
 * command frame per {@link #advance} call. It is step-driven and AWT-free, so the game loop calls
 * {@link #advance} each tick and renders {@link #world()}; the engine pieces underneath are the
 * fully-tested F1-F5 stack. See OnlineMode.md (F6).
 *
 * <p>v1 uses a one-step-per-command-frame, zero-input-delay lockstep (each {@link #advance} progresses a
 * frame only once every peer's input for it is in). That is ideal on localhost/LAN; raising the input
 * delay / batching for higher-latency internet is the playtest-tuning follow-up noted in OnlineMode.md.
 */
@Slf4j
public final class OnlineSession implements AutoCloseable {

    private static final long WELCOME_TIMEOUT_MS = 5000;

    private final GameMode mode;
    private final PlayWorld world;
    private final int localSlot;
    private final String matchCode;

    private final OnlineHost host;          // HOST mode
    private final OnlineClient client;      // CLIENT mode
    private final TcpServer server;         // HOST mode
    private final LanBeacon beacon;         // HOST mode
    private final TcpClientTransport transport; // CLIENT mode

    private long frame;
    private boolean dispatched; // local input already sent/submitted for the current frame

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
        var onlineHost = new OnlineHost(world, server, 1, 0);
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
        var onlineClient = new OnlineClient(world, transport, 1);
        log.info("Joined match {} as slot {} ({} players)",
                welcome.matchCode(), welcome.slot(), welcome.playerCount());
        return new OnlineSession(GameMode.CLIENT, world, welcome.slot(), welcome.matchCode(),
                null, onlineClient, null, null, transport);
    }

    // -- per-tick drive -----------------------------------------------------

    /** Advances one command frame from the local keyboard; returns whether the world actually stepped. */
    public boolean advance(InputBridge input) {
        return advanceWith(localInput(input));
    }

    /** Advances one command frame from an explicit local input (used by tests); returns whether stepped. */
    boolean advanceWith(TickInput local) {
        if (mode == GameMode.HOST) {
            if (!dispatched) {
                host.submitLocalInput(frame, local);
                dispatched = true;
            }
            host.pumpInbound();
            if (host.tryAdvance() != null) {
                frame++;
                dispatched = false;
                return true;
            }
            return false;
        }
        if (!dispatched) {
            client.sendLocalInput(frame, local);
            dispatched = true;
        }
        client.pump();
        if (client.lastAppliedFrame() >= frame) {
            frame++;
            dispatched = false;
            return true;
        }
        return false;
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
            client.pump();
        }
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
