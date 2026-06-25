// src/test/java/pl/mzebrows/shoots/net/OnlineDesyncTest.java
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
 * The desync safety net over TCP: matching client hashes leave the host's desync counter at zero, while
 * a single mismatching hash is detected and flagged (frame + count). See OnlineMode.md (F5).
 */
class OnlineDesyncTest {

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

    private TickInput tick(int slot, long frame) {
        return new TickInput(slot == 0 ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, frame % 6 == 0);
    }

    @Test
    @Timeout(30)
    void matchingHashesPassAndAMismatchIsDetected() throws Exception {
        long seed = 777L;
        var hostWorld = new PlayWorld(config(seed));

        try (var server = new TcpServer(0, PLAYERS, seed, "ABCXYZ")) {
            server.start();
            try (var transport = TcpClientTransport.connect("127.0.0.1", server.port(), "C")) {
                transport.awaitWelcome(AWAIT_MS);
                var client = new OnlineClient(new PlayWorld(config(seed)), transport, STEPS_PER_FRAME);
                var host = new OnlineHost(hostWorld, server, STEPS_PER_FRAME, 0);

                for (long f = 0; f < 5; f++) {
                    client.sendLocalInput(f, tick(1, f));
                    host.submitLocalInput(f, tick(0, f));
                    awaitHostRelease(host, f);
                    awaitClientApplied(client, f);
                    client.sendHash(f); // correct hash (worlds are identical)
                }

                pumpHost(host, 400); // let the host consume the correct hashes
                assertThat(host.desyncCount()).as("matching hashes => no desync").isZero();

                // Inject a bogus hash for a frame the host has recorded.
                transport.send(new NetMessage.Hash(0, 0xBADBAD0000L));
                awaitDesync(host);
                assertThat(host.desyncCount()).isGreaterThanOrEqualTo(1);
                assertThat(host.lastDesyncFrame()).isEqualTo(0);
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
        throw new AssertionError("host did not release frame " + frame);
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
        throw new AssertionError("client did not apply frame " + frame);
    }

    private void pumpHost(OnlineHost host, long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            host.pumpInbound();
            sleepBriefly();
        }
    }

    private void awaitDesync(OnlineHost host) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            host.pumpInbound();
            if (host.desyncCount() > 0) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("host did not detect the injected desync");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
