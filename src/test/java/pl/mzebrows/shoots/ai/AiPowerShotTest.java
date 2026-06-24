// src/test/java/pl/mzebrows/shoots/ai/AiPowerShotTest.java
package pl.mzebrows.shoots.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.AiSkillToggles;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Feature #4: AIs use the charged power shot, gated by config + the per-AI {@code powerShotTendency}.
 * A strong AI fires power discs when allowed; with the AI power shot disabled it never does.
 */
class AiPowerShotTest {

    private static final long SEED = 2024L;

    private static ColorPalette palette() {
        return new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
    }

    private static GameConfig config(AiConfig ai) {
        return new GameConfig(2, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette(), ai);
    }

    private static PlayerAiController controller(PlayWorld world, int playerId, AiDifficulty difficulty) {
        AiSkills skills = AiSkillsFactory.create(difficulty, world.seed(), playerId);
        var targeting = new AiTargeting(world.tracer(), world.config().disc().maxBounces());
        return new PlayerAiController(playerId, skills, targeting, 24, world.seed());
    }

    private static boolean firesPowerDiscWithin(PlayWorld world, PlayerAiController ai, int ticks) {
        for (int t = 0; t < ticks; t++) {
            ai.think(world);
            world.step();
            for (Entity d : world.discs()) {
                if (d.isPowered()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void strongAiFiresPowerShotsWhenAllowed() {
        // Power enabled, no minimum range gate, so any of the strong AI's shots may roll a power disc.
        var world = new PlayWorld(config(new AiConfig(24, 4, true, true, 0, false, AiSkillToggles.allEnabled())), SEED);
        var ai = controller(world, 0, AiDifficulty.VERY_HARD);

        assertThat(firesPowerDiscWithin(world, ai, 3000)).isTrue();
    }

    @Test
    void aiNeverFiresPowerShotsWhenDisabled() {
        // Same strong AI, but the AI power shot is switched off in config -> only normal discs.
        var world = new PlayWorld(config(new AiConfig(24, 4, true, false, 0, false, AiSkillToggles.allEnabled())), SEED);
        var ai = controller(world, 0, AiDifficulty.VERY_HARD);

        assertThat(firesPowerDiscWithin(world, ai, 3000)).isFalse();
    }
}
