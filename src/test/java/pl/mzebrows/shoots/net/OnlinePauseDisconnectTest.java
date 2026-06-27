// src/test/java/pl/mzebrows/shoots/net/OnlinePauseDisconnectTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
 * Covers the disconnect/pause robustness fixes over REAL localhost TCP: a client's pause request is
 * re-broadcast authoritatively and observed by every peer (#3); and a mid-match client drop no longer
 * stalls the lockstep gate -- the host plays the gone slot idle and keeps advancing (#4).
 */
class OnlinePauseDisconnectTest {

    private static final int PLAYERS = 2;
    private static final int STEPS_PER_FRAME = 4;
    private static final long AWAIT_MS = 4000;

    private GameConfig config(long seed) {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(PLAYERS, seed,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new AiConfig(24, 4, true));
    }

    @Test
    @Timeout(30)
    void clientPauseRequestIsBroadcastAndSeenByEveryPeer() throws Exception {
        long seed = 555L;
        try (var server = new TcpServer(0, PLAYERS, seed, "ABCXYZ")) {
            server.start();
            try (var clientTransport = TcpClientTransport.connect("127.0.0.1", server.port(), "Client")) {
                clientTransport.awaitWelcome(AWAIT_MS);
                var host = new OnlineHost(new PlayWorld(config(seed)), server, STEPS_PER_FRAME, 0);
                var client = new OnlineClient(new PlayWorld(config(seed)), clientTransport, STEPS_PER_FRAME);
                assertThat(host.pausedBy()).isEqualTo(-1);

                // Client (slot 1) asks to pause; the host records + re-broadcasts it; both peers observe slot 1.
                client.sendPause(1, true);
                awaitTrue(() -> { host.pumpInbound(); return host.pausedBy() == 1; }, "host sees pause");
                awaitTrue(() -> { client.pump(); return client.pausedBy() == 1; }, "client sees pause");

                // Resume clears it everywhere.
                client.sendPause(1, false);
                awaitTrue(() -> { host.pumpInbound(); return host.pausedBy() == -1; }, "host sees resume");
                awaitTrue(() -> { client.pump(); return client.pausedBy() == -1; }, "client sees resume");
            }
        }
    }

    @Test
    @Timeout(30)
    void hostKeepsAdvancingAfterAClientDrops() throws Exception {
        long seed = 9001L;
        try (var server = new TcpServer(0, PLAYERS, seed, "ABCXYZ")) {
            server.start();
            var clientTransport = TcpClientTransport.connect("127.0.0.1", server.port(), "Client");
            clientTransport.awaitWelcome(AWAIT_MS);
            var host = new OnlineHost(new PlayWorld(config(seed)), server, STEPS_PER_FRAME, 0);
            var client = new OnlineClient(new PlayWorld(config(seed)), clientTransport, STEPS_PER_FRAME);

            // Frame 0 with both peers present.
            client.sendLocalInput(0, new TickInput(PlayWorld.AimInput.LEFT, false));
            host.submitLocalInput(0, new TickInput(PlayWorld.AimInput.RIGHT, false));
            awaitTrue(() -> { host.pumpInbound(); host.tryAdvance(); return host.lastReleasedFrame() >= 0; },
                    "host releases frame 0");

            // The client vanishes mid-match.
            clientTransport.close();

            // Without the fix the host would stall forever waiting on slot 1; instead it fills neutral and runs on.
            long target = host.lastReleasedFrame() + 5;
            for (long f = host.lastReleasedFrame() + 1; f <= target; f++) {
                host.submitLocalInput(f, new TickInput(PlayWorld.AimInput.RIGHT, false));
            }
            awaitTrue(() -> { host.pumpInbound(); host.tryAdvance(); return host.lastReleasedFrame() >= target; },
                    "host keeps advancing past the drop");
            assertThat(host.leftSlots()[1]).as("dropped slot is marked left/idle").isTrue();

            server.close();
        }
    }

    private void awaitTrue(java.util.function.BooleanSupplier cond, String what) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("timed out waiting for: " + what);
    }
}
