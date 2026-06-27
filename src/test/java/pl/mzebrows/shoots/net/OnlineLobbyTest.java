// src/test/java/pl/mzebrows/shoots/net/OnlineLobbyTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * F7 waiting room over real localhost TCP: a host opens a lobby, a client joins, the roster syncs, the
 * host STARTs, and both peers build a bit-identical world from the master seed. Also covers the F7f exit
 * semantics: a client leaving frees its slot; the host leaving drops the client.
 */
class OnlineLobbyTest {

    private static final long TIMEOUT_MS = 8000;

    private GameConfig base() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(2, 1L,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new AiConfig(24, 4, true));
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + TIMEOUT_MS + " ms");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private TickInput tick(int slot, long frame) {
        boolean left = ((frame / 5) + slot) % 2 == 0;
        return new TickInput(left ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, frame % 7 == 0);
    }

    @Test
    @Timeout(40)
    void rosterSyncsThenStartBuildsIdenticalWorlds() throws Exception {
        GameConfig base = base();
        OnlineLobby host = OnlineLobby.host(base, 4, 0, "Host");
        int port = host.port();
        OnlineLobby client = OnlineLobby.joinAddress(base, "127.0.0.1", port, "Client");
        try {
            // Host sees the client; roster fills slots 0 and 1; contiguous so START is allowed.
            awaitTrue(() -> { host.pump(); return host.occupiedCount() == 2; });
            assertThat(host.roster()[0]).isEqualTo("Host");
            assertThat(host.roster()[1]).isEqualTo("Client");
            assertThat(host.canStart()).isTrue();

            // The client receives the host's roster broadcast.
            awaitTrue(() -> { client.pump(); return !client.roster()[1].isEmpty(); });

            assertThat(host.startMatch()).isTrue();
            OnlineSession hostSession = host.takeStarted();
            awaitTrue(() -> { client.pump(); return client.isStarted(); });
            OnlineSession clientSession = client.takeStarted();

            try (hostSession; clientSession) {
                assertThat(clientSession.matchCode()).isEqualTo(hostSession.matchCode());
                assertThat(hostSession.localSlot()).isZero();
                assertThat(clientSession.localSlot()).isEqualTo(1);
                // Built from the same master seed -> identical before any input.
                assertThat(WorldHash.of(clientSession.world())).isEqualTo(WorldHash.of(hostSession.world()));

                int frames = 30;
                long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
                while (hostSession.frame() < frames || clientSession.frame() < frames) {
                    hostSession.advanceWith(tick(0, hostSession.frame()));
                    clientSession.advanceWith(tick(1, clientSession.frame()));
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("lockstep stuck");
                    }
                    Thread.sleep(0, 200_000);
                }
                // Input delay lets the host run a few frames ahead; align to a common frame before comparing.
                long target = Math.max(hostSession.frame(), clientSession.frame());
                while (hostSession.frame() < target || clientSession.frame() < target) {
                    if (hostSession.frame() < target) {
                        hostSession.advanceWith(tick(0, hostSession.frame()));
                    }
                    if (clientSession.frame() < target) {
                        clientSession.advanceWith(tick(1, clientSession.frame()));
                    }
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("could not align frames");
                    }
                    Thread.sleep(0, 200_000);
                }
                assertThat(clientSession.frame()).isEqualTo(hostSession.frame());
                assertThat(WorldHash.of(clientSession.world()))
                        .as("host and client worlds stay identical at frame %d", hostSession.frame())
                        .isEqualTo(WorldHash.of(hostSession.world()));
            }
        } finally {
            host.close();
            client.close();
        }
    }

    @Test
    @Timeout(40)
    void clientLeavingFreesItsSlotForReuse() throws Exception {
        GameConfig base = base();
        try (OnlineLobby host = OnlineLobby.host(base, 4, 0, "Host")) {
            int port = host.port();
            OnlineLobby client = OnlineLobby.joinAddress(base, "127.0.0.1", port, "Client");
            awaitTrue(() -> { host.pump(); return host.occupiedCount() == 2; });

            client.close(); // client leaves the waiting room
            awaitTrue(() -> { host.pump(); return host.occupiedCount() == 1; });
            assertThat(host.roster()[1]).isEmpty(); // slot 1 is OPEN again

            // The freed slot is reused by the next joiner.
            try (OnlineLobby rejoin = OnlineLobby.joinAddress(base, "127.0.0.1", port, "Rejoin")) {
                awaitTrue(() -> { host.pump(); return host.occupiedCount() == 2; });
                assertThat(host.roster()[1]).isEqualTo("Rejoin");
            }
        }
    }

    @Test
    @Timeout(40)
    void hostLeavingDropsTheClient() throws Exception {
        GameConfig base = base();
        OnlineLobby host = OnlineLobby.host(base, 4, 0, "Host");
        int port = host.port();
        try (OnlineLobby client = OnlineLobby.joinAddress(base, "127.0.0.1", port, "Client")) {
            awaitTrue(() -> { host.pump(); return host.occupiedCount() == 2; });

            host.close(); // host tears down the room
            awaitTrue(() -> { client.pump(); return client.hostLeft(); });
            assertThat(client.hostLeft()).isTrue();
            assertThat(client.isStarted()).isFalse();
        }
    }

    @Test
    @Timeout(40)
    void startPropagatesHostRoundPacingToClient() throws Exception {
        // Host chooses a 5s / 3-round match; the client's LOCAL default is 15s / 2 rounds -- it must adopt the
        // host's pacing from START, proving the menu's round-time/limit choice reaches every peer (#7/#1).
        GameConfig hostBase = base().withRound(new RoundConfig(5, 3, 2, 1));
        GameConfig clientBase = base();
        OnlineLobby host = OnlineLobby.host(hostBase, 4, 0, "Host");
        int port = host.port();
        OnlineLobby client = OnlineLobby.joinAddress(clientBase, "127.0.0.1", port, "Client");
        try {
            awaitTrue(() -> { host.pump(); return host.occupiedCount() == 2; });
            awaitTrue(() -> { client.pump(); return !client.roster()[1].isEmpty(); });

            assertThat(host.startMatch()).isTrue();
            OnlineSession hostSession = host.takeStarted();
            awaitTrue(() -> { client.pump(); return client.isStarted(); });
            OnlineSession clientSession = client.takeStarted();

            try (hostSession; clientSession) {
                assertThat(hostSession.world().config().round().roundTimeSeconds()).isEqualTo(5);
                assertThat(hostSession.world().config().round().roundLimit()).isEqualTo(3);
                assertThat(clientSession.world().config().round().roundTimeSeconds())
                        .as("client adopts the host's round time, not its own 15s default")
                        .isEqualTo(5);
                assertThat(clientSession.world().config().round().roundLimit()).isEqualTo(3);
            }
        } finally {
            host.close();
            client.close();
        }
    }
}
