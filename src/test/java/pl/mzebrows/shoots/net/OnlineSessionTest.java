// src/test/java/pl/mzebrows/shoots/net/OnlineSessionTest.java
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
 * F6 engine: the {@link OnlineSession} bootstrap (host + join) and its per-tick driver keep host and
 * client worlds in sync over real localhost TCP. The host seed is randomised internally, so a matching
 * end state proves the seed flowed through WELCOME and the whole F1–F5 stack runs from the session API.
 */
class OnlineSessionTest {

    private static final int FRAMES = 40;
    private static final long TIMEOUT_MS = 12_000;

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

    private TickInput tick(int slot, long frame) {
        boolean left = ((frame / 5) + slot) % 2 == 0;
        return new TickInput(left ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, frame % (8 + slot) == 0);
    }

    @Test
    @Timeout(40)
    void hostAndClientSessionsStayInSyncOverTcp() throws Exception {
        GameConfig base = base();
        try (var hostSession = OnlineSession.host(base, 2, 0, "Host")) {
            try (var clientSession = OnlineSession.join(base, "127.0.0.1", hostSession.port(), "Client")) {
                assertThat(clientSession.matchCode()).isEqualTo(hostSession.matchCode());
                assertThat(hostSession.localSlot()).isZero();
                assertThat(clientSession.localSlot()).isEqualTo(1);
                // Built from the same (WELCOME-delivered) seed -> identical before any input.
                assertThat(WorldHash.of(clientSession.world())).isEqualTo(WorldHash.of(hostSession.world()));

                long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
                while (hostSession.frame() < FRAMES || clientSession.frame() < FRAMES) {
                    hostSession.advanceWith(tick(0, hostSession.frame()));
                    clientSession.advanceWith(tick(1, clientSession.frame()));
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("stuck (host frame " + hostSession.frame()
                                + ", client frame " + clientSession.frame() + ")");
                    }
                    Thread.sleep(0, 200_000);
                }

                assertThat(WorldHash.of(clientSession.world()))
                        .as("host and client worlds must match after %d frames", FRAMES)
                        .isEqualTo(WorldHash.of(hostSession.world()));
            }
        }
    }
}
