// src/test/java/pl/mzebrows/shoots/ai/AiPlayersTest.java
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

class AiPlayersTest {

    private static final long SEED = 77L;

    private static PlayWorld world(int players) {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
        var config = new GameConfig(players, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette,
                new pl.mzebrows.shoots.config.AiConfig(24, 4, true));
        return new PlayWorld(config, SEED);
    }

    @Test
    void aiFillsTheHighestSlotsHumansKeepLowOnes() {
        // 3 players, 2 AI -> P0 human, P1 & P2 AI.
        var ai = AiPlayers.build(world(3), 2, AiDifficulty.NORMAL, 24);

        assertThat(ai.count()).isEqualTo(2);
        assertThat(ai.isAiSlot(0)).isFalse();
        assertThat(ai.isAiSlot(1)).isTrue();
        assertThat(ai.isAiSlot(2)).isTrue();
    }

    @Test
    void zeroAiYieldsAnEmptyHolder() {
        var ai = AiPlayers.build(world(2), 0, AiDifficulty.HARD, 24);

        assertThat(ai.isEmpty()).isTrue();
        assertThat(ai.count()).isZero();
    }

    @Test
    void aiCountIsClampedToThePlayerCount() {
        // Asking for more AI than players caps at the player count (all-AI match).
        var ai = AiPlayers.build(world(2), 5, AiDifficulty.EASY, 24);

        assertThat(ai.count()).isEqualTo(2);
        assertThat(ai.isAiSlot(0)).isTrue();
        assertThat(ai.isAiSlot(1)).isTrue();
    }

    @Test
    void allHumanFourPlayerHasNoAiSlots() {
        var ai = AiPlayers.build(world(4), 0, AiDifficulty.VERY_HARD, 24);

        for (int p = 0; p < 4; p++) {
            assertThat(ai.isAiSlot(p)).isFalse();
        }
    }

    @Test
    void thinkDrivesEveryControllerWithoutError() {
        var world = world(2);
        var ai = AiPlayers.build(world, 1, AiDifficulty.NORMAL, 24);

        for (int tick = 0; tick < 200; tick++) {
            ai.think(world);
            world.step();
        }
        // The single AI is slot 1; its disc cap is respected.
        assertThat(world.activeDiscs(1))
                .isLessThanOrEqualTo(ai.controllers().getFirst().skills().maxDiscsInFlight());
    }

    @Test
    void noneHolderIsInert() {
        var world = world(2);
        var ai = AiPlayers.none();
        ai.think(world); // must not throw
        world.step();
        assertThat(world.totalActiveDiscs()).isZero();
    }
}
