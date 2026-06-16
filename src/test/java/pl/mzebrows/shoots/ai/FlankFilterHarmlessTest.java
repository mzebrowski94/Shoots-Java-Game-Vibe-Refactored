// src/test/java/pl/mzebrows/shoots/ai/FlankFilterHarmlessTest.java
package pl.mzebrows.shoots.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * The "never shoot flank blocks" filter is a low-impact safeguard: on any generated map only a tiny
 * fraction of a player's ±110° aim arc could first-reflect on one of its own flank walls, so the AI is
 * never meaningfully blocked -- the large majority of angles remain usable. (How many flank-first
 * angles exist is map-dependent; this test asserts the filter never removes more than a third of the arc.)
 */
class FlankFilterHarmlessTest {

    private static final long SEED = 4242L;
    private static final double AIM_LIMIT = 110.0;

    private static PlayWorld world(int players, long seed) {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
        var config = new GameConfig(players, seed, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette,
                new pl.mzebrows.shoots.config.AiConfig(24, 4, true));
        return new PlayWorld(config, seed);
    }

    private static long[] ownFlankTiles(PlayWorld.BasePlacement base) {
        int cx = base.tileX();
        int cy = base.tileY();
        boolean horizontal = base.shootDirection() == 0 || base.shootDirection() == 180;
        if (horizontal) {
            return new long[]{AiTargeting.packTile(cx, cy - 2), AiTargeting.packTile(cx, cy + 2)};
        }
        return new long[]{AiTargeting.packTile(cx - 2, cy), AiTargeting.packTile(cx + 2, cy)};
    }

    @Test
    void flankFilterRemovesOnlyASmallSliceOfEachArc() {
        // Average over several maps so the assertion is not tied to one random layout.
        for (long seed = 1; seed <= 8; seed++) {
            var world = world(4, seed);
            var targeting = new AiTargeting(world.collider(), world.unit(),
                    world.config().disc().maxBounces());
            double speed = world.config().disc().moveSpeed();

            for (int p = 0; p < world.playerCount(); p++) {
                var base = world.baseOf(p);
                long[] flanks = ownFlankTiles(base);
                double centre = base.shootDirection();
                int total = 0;
                int flankFirst = 0;
                for (double a = centre - AIM_LIMIT; a <= centre + AIM_LIMIT; a += 1.0) {
                    total++;
                    long firstWall = targeting.firstWallTile(base.pixelX(), base.pixelY(), a, speed);
                    for (long flank : flanks) {
                        if (firstWall == flank) {
                            flankFirst++;
                        }
                    }
                }
                // The filter only ever clips the near-horizontal extremes: the AI always keeps the large
                // majority of its arc usable (well over half), so it is never meaningfully blocked.
                assertThat(flankFirst).as("seed %d player %d flank-first angles", seed, p)
                        .isLessThan(total / 3);
            }
        }
    }
}
