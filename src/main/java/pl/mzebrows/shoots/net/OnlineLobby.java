// src/main/java/pl/mzebrows/shoots/net/OnlineLobby.java
package pl.mzebrows.shoots.net;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * The pre-match WAITING ROOM for one peer (F7). A HOST opens the listen server + LAN beacon and tracks the
 * live roster as clients join/leave (broadcasting it as {@link NetMessage.Lobby}); a CLIENT connects, learns
 * its slot from {@code WELCOME}, and waits for roster updates and the host's {@link NetMessage.Start}. The
 * world is NOT built here -- it is created from the master seed only when the host presses START, so the
 * player count matches the slots actually filled. On start this hands its open transport/server straight to
 * an {@link OnlineSession} (no reconnect). AWT-free and driven one {@link #pump()} per frame by the menu.
 *
 * <p>Slots are kept contiguous (the server assigns the lowest free slot and reuses freed ones), so a peer's
 * player id equals its lobby slot and the host may only START when the filled slots are gap-free.
 */
@Slf4j
public final class OnlineLobby implements AutoCloseable {

    private static final long WELCOME_TIMEOUT_MS = 5000;
    private static final int MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 4;

    private final GameMode mode;
    private final GameConfig base;
    private final String localName;

    private int maxPlayers;
    private long seed;
    private String matchCode;
    private int localSlot;

    // HOST only
    private TcpServer server;
    private LanBeacon beacon;

    // CLIENT only
    private TcpClientTransport transport;

    private String[] roster;          // by slot; "" == open
    private boolean hostLeft;         // CLIENT: the host closed the room
    private OnlineSession started;    // non-null once the match begins
    private boolean handedOff;        // the started session now owns the transport/server

    private OnlineLobby(GameMode mode, GameConfig base, String localName) {
        this.mode = mode;
        this.base = base;
        this.localName = localName;
    }

    // -- bootstrap ----------------------------------------------------------

    /** Opens a host waiting room: starts the listen server and LAN beacon; the world waits for START. */
    public static OnlineLobby host(GameConfig base, int maxPlayers, int port, String name) throws IOException {
        var lobby = new OnlineLobby(GameMode.HOST, base, name);
        lobby.maxPlayers = Math.max(MIN_PLAYERS, Math.min(DEFAULT_MAX_PLAYERS, maxPlayers));
        lobby.seed = new Random().nextLong();
        lobby.matchCode = MatchCode.generate();
        lobby.localSlot = TcpServer.HOST_SLOT;
        lobby.roster = openRoster(lobby.maxPlayers);
        lobby.roster[TcpServer.HOST_SLOT] = name;

        var server = new TcpServer(port, lobby.maxPlayers, lobby.seed, lobby.matchCode);
        server.start();
        lobby.server = server;
        var beacon = LanBeacon.broadcast(1000, lobby::announcement);
        beacon.start();
        lobby.beacon = beacon;
        log.info("Hosting lobby {} on port {} (up to {} players)", lobby.matchCode, server.port(), lobby.maxPlayers);
        return lobby;
    }

    /** Joins a host at {@code ip:port} and enters its waiting room (the world waits for the host's START). */
    public static OnlineLobby joinAddress(GameConfig base, String ip, int port, String name) throws IOException {
        var transport = TcpClientTransport.connect(ip, port, name);
        NetMessage.Welcome welcome = transport.awaitWelcome(WELCOME_TIMEOUT_MS);
        var lobby = new OnlineLobby(GameMode.CLIENT, base, name);
        lobby.transport = transport;
        lobby.localSlot = welcome.slot();
        lobby.seed = welcome.seed();
        lobby.matchCode = welcome.matchCode();
        lobby.maxPlayers = Math.max(welcome.playerCount(), welcome.slot() + 1);
        lobby.roster = openRoster(lobby.maxPlayers);
        lobby.roster[welcome.slot()] = name;
        log.info("Joined lobby {} as slot {}", welcome.matchCode(), welcome.slot());
        return lobby;
    }

    // -- per-frame drive ----------------------------------------------------

    /** Advances the waiting room one frame: refresh/broadcast the roster (host) or apply host messages (client). */
    public void pump() {
        if (started != null) {
            return;
        }
        if (mode == GameMode.HOST) {
            pumpHost();
        } else {
            pumpClient();
        }
    }

    private void pumpHost() {
        boolean changed = server.removeDisconnected();
        String[] now = openRoster(maxPlayers);
        now[TcpServer.HOST_SLOT] = localName;
        server.fillRoster(now);
        if (!Arrays.equals(now, roster)) {
            roster = now;
            changed = true;
        }
        if (changed) {
            server.broadcast(new NetMessage.Lobby(roster.clone()));
        }
        while (server.poll() != null) {
            // Clients send nothing before the match starts; drain to keep the queue clear.
        }
    }

    private void pumpClient() {
        NetMessage message;
        while ((message = transport.poll()) != null) {
            switch (message) {
                case NetMessage.Lobby lobby -> roster = lobby.slotNames();
                case NetMessage.Start start -> {
                    startAsClient(start);
                    return;
                }
                default -> { /* WELCOME consumed at connect; FRAMEs arrive after START */ }
            }
        }
        if (!transport.isOpen()) {
            hostLeft = true;
        }
    }

    private void startAsClient(NetMessage.Start start) {
        int playerId = indexOf(start.orderedSlots(), localSlot);
        if (playerId < 0) {
            log.warn("START did not include our slot {}; leaving lobby {}", localSlot, matchCode);
            hostLeft = true;
            return;
        }
        GameConfig cfg = base.withRound(roundFrom(start)).withPlayerNumber(start.orderedSlots().length).withSeed(start.seed());
        var world = new PlayWorld(cfg);
        started = OnlineSession.startedClient(world, transport, playerId, matchCode);
        log.info("Match {} starting: {} players, local player id {}", matchCode, start.orderedSlots().length, playerId);
    }

    // -- host actions -------------------------------------------------------

    /**
     * Host: broadcasts START to every client and builds the shared world, returning whether the match began.
     * Refuses unless at least {@value #MIN_PLAYERS} contiguous slots are filled (see {@link #canStart()}).
     */
    public boolean startMatch() {
        if (mode != GameMode.HOST || started != null || !canStart()) {
            return false;
        }
        int[] slots = occupiedSlots();
        // Carry the host's chosen round pacing so every client builds an identical match (#7).
        server.broadcast(new NetMessage.Start(seed, slots, base.round().roundTimeSeconds(), base.round().roundLimit()));
        GameConfig cfg = base.withPlayerNumber(slots.length).withSeed(seed);
        var world = new PlayWorld(cfg);
        started = OnlineSession.startedHost(world, server, beacon, matchCode);
        log.info("Starting match {} with {} players", matchCode, slots.length);
        return true;
    }

    // -- queries ------------------------------------------------------------

    /** Whether the match has begun (host pressed START, or a client received START). */
    public boolean isStarted() {
        return started != null;
    }

    /**
     * Detaches the started session so the caller (menu) can drive the match; this lobby no longer owns the
     * transport/server, so a later {@link #close()} leaves them open for the session.
     */
    public OnlineSession takeStarted() {
        handedOff = true;
        return started;
    }

    /** Host: enough filled, gap-free slots to start (>= {@value #MIN_PLAYERS} and contiguous from 0). */
    public boolean canStart() {
        if (mode != GameMode.HOST) {
            return false;
        }
        int[] slots = occupiedSlots();
        if (slots.length < MIN_PLAYERS) {
            return false;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != i) {
                return false; // an interior slot is open; wait for it to be refilled before starting
            }
        }
        return true;
    }

    /** The CLIENT saw the host close the room (or be dropped from START); the menu returns to the main menu. */
    public boolean hostLeft() {
        return hostLeft;
    }

    /** Current roster by slot ({@code ""} == open); a defensive copy. */
    public String[] roster() {
        return roster.clone();
    }

    public int occupiedCount() {
        return occupiedSlots().length;
    }

    public String matchCode() {
        return matchCode;
    }

    public GameMode mode() {
        return mode;
    }

    public int localSlot() {
        return localSlot;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    /** Host's bound TCP port (host mode), or {@code -1} for a client. */
    public int port() {
        return server != null ? server.port() : -1;
    }

    @Override
    public void close() {
        if (handedOff) {
            return; // the started OnlineSession owns the transport/server/beacon now
        }
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

    // -- helpers ------------------------------------------------------------

    /** Overlays the host's START round pacing onto the local base round config; {@code 0} fields keep the local default. */
    private RoundConfig roundFrom(NetMessage.Start start) {
        RoundConfig local = base.round();
        int time = start.roundTimeSeconds() > 0 ? start.roundTimeSeconds() : local.roundTimeSeconds();
        int limit = start.roundLimit() > 0 ? start.roundLimit() : local.roundLimit();
        return new RoundConfig(time, limit, local.roundEndDelay(), local.animationTime());
    }

    private LanAnnouncement announcement() {
        boolean joinable = started == null && occupiedCount() < maxPlayers;
        return new LanAnnouncement(matchCode, localName, server.port(), occupiedCount(), joinable);
    }

    private int[] occupiedSlots() {
        int count = 0;
        for (String name : roster) {
            if (!name.isEmpty()) {
                count++;
            }
        }
        int[] slots = new int[count];
        int idx = 0;
        for (int s = 0; s < roster.length; s++) {
            if (!roster[s].isEmpty()) {
                slots[idx++] = s;
            }
        }
        return slots;
    }

    private static int indexOf(int[] slots, int value) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static String[] openRoster(int size) {
        String[] roster = new String[size];
        Arrays.fill(roster, "");
        return roster;
    }
}
