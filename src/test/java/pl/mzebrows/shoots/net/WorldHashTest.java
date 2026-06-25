// src/test/java/pl/mzebrows/shoots/net/WorldHashTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.world.PlayWorld;

/** The desync detector: identical lockstep worlds hash equal every frame; a divergence changes it. */
class WorldHashTest {

    private static final long SEED = 42L;
    private static final int PLAYERS = 2;
    private static final int TICKS = 300;

    private GameConfig config() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(PLAYERS, SEED,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new AiConfig(24, 4, true));
    }

    private PlayWorld.AimInput aim(int p, int t) {
        return ((t / 15) + p) % 2 == 0 ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT;
    }

    @Test
    void identicalWorldsHashEqualEveryTick() {
        PlayWorld a = new PlayWorld(config());
        PlayWorld b = new PlayWorld(config());
        assertThat(WorldHash.of(a)).isEqualTo(WorldHash.of(b));

        for (int t = 0; t < TICKS; t++) {
            for (int p = 0; p < PLAYERS; p++) {
                a.applyInput(p, aim(p, t), t % 37 == p * 11);
                b.applyInput(p, aim(p, t), t % 37 == p * 11);
            }
            a.step();
            b.step();
            assertThat(WorldHash.of(a)).as("hash diverged at tick %d", t).isEqualTo(WorldHash.of(b));
        }
    }

    @Test
    void aDivergentInputChangesTheHash() {
        PlayWorld a = new PlayWorld(config());
        PlayWorld b = new PlayWorld(config());

        // Drive both identically for a while, then give B one different shot -> hashes must part.
        for (int t = 0; t < 40; t++) {
            a.applyInput(0, PlayWorld.AimInput.LEFT, t == 5);
            b.applyInput(0, PlayWorld.AimInput.LEFT, t == 5);
            a.step();
            b.step();
        }
        assertThat(WorldHash.of(a)).isEqualTo(WorldHash.of(b));

        a.applyInput(1, PlayWorld.AimInput.NONE, true);   // only A fires for player 1
        b.applyInput(1, PlayWorld.AimInput.NONE, false);
        a.step();
        b.step();

        assertThat(WorldHash.of(a)).as("hash must detect the divergence").isNotEqualTo(WorldHash.of(b));
    }
}
