// src/test/java/pl/mzebrows/shoots/net/FourPlayerOnlineTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
 * Proves "up to 4 players": a host (slot 0) plus THREE clients run a match over real localhost TCP and
 * stay bit-identical (compared via {@link WorldHash}) every command frame. Exercises slot assignment,
 * 4-slot input aggregation in the {@link LockstepCoordinator}, and broadcast/apply at full size.
 */
class FourPlayerOnlineTest {

    private static final int PLAYERS = 4;
    private static final int CLIENTS = PLAYERS - 1;
    private static final int STEPS_PER_FRAME = 4;
    private static final int FRAMES = 40;
    private static final long AWAIT_MS = 5000;

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

    private TickInput tickFor(int slot, long frame) {
        boolean left = ((frame / (4 + slot)) + slot) % 2 == 0;
        return new TickInput(left ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, frame % (7 + slot) == 0);
    }

    @Test
    @Timeout(40)
    void fourPeersStayBitIdenticalOverTcp() throws Exception {
        long seed = 20260625L;
        var hostWorld = new PlayWorld(config(seed));

        var clientTransports = new ArrayList<TcpClientTransport>();
        var clients = new ArrayList<OnlineClient>();
        try (var server = new TcpServer(0, PLAYERS, seed, "ABCXYZ")) {
            server.start();
            int port = server.port();
            try {
                var host = new OnlineHost(hostWorld, server, STEPS_PER_FRAME, 0);

                for (int c = 0; c < CLIENTS; c++) {
                    var transport = TcpClientTransport.connect("127.0.0.1", port, "C" + c);
                    clientTransports.add(transport);
                    NetMessage.Welcome welcome = transport.awaitWelcome(AWAIT_MS);
                    assertThat(welcome.slot()).isEqualTo(c + 1);     // host=0, clients=1,2,3
                    assertThat(welcome.playerCount()).isEqualTo(PLAYERS);
                    clients.add(new OnlineClient(new PlayWorld(config(welcome.seed())), transport, STEPS_PER_FRAME));
                }

                for (long frame = 0; frame < FRAMES; frame++) {
                    host.submitLocalInput(frame, tickFor(TcpServer.HOST_SLOT, frame));
                    for (int c = 0; c < CLIENTS; c++) {
                        clients.get(c).sendLocalInput(frame, tickFor(c + 1, frame));
                    }
                    awaitHostRelease(host, frame);
                    for (OnlineClient client : clients) {
                        awaitClientApplied(client, frame);
                    }
                    long hostHash = WorldHash.of(hostWorld);
                    for (int c = 0; c < CLIENTS; c++) {
                        assertThat(WorldHash.of(clients.get(c).world()))
                                .as("client %d diverged from host at frame %d", c, frame)
                                .isEqualTo(hostHash);
                    }
                }
            } finally {
                for (TcpClientTransport t : clientTransports) {
                    t.close();
                }
            }
        }
    }

    private void awaitHostRelease(OnlineHost host, long frame) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            host.pumpInbound();
            host.tryAdvance();
            if (host.lastReleasedFrame() >= frame) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("host did not release frame " + frame + " (needs all 4 inputs)");
    }

    private void awaitClientApplied(OnlineClient client, long frame) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            client.pump();
            if (client.lastAppliedFrame() >= frame) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("a client did not apply frame " + frame);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
