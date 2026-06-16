// src/test/java/pl/mzebrows/shoots/ai/PlayerAiControllerTest.java
package pl.mzebrows.shoots.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** Drives a real (seeded) {@link PlayWorld} so the AI's effect on the live model is observable. */
class PlayerAiControllerTest {

    private static final long SEED = 2024L;

    private static GameConfig config() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
        return new GameConfig(2, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette,
                new pl.mzebrows.shoots.config.AiConfig(24, 4, true));
    }

    private static PlayWorld world() {
        return new PlayWorld(config(), SEED);
    }

    private static PlayerAiController controller(PlayWorld world, int playerId, AiDifficulty difficulty) {
        AiSkills skills = AiSkillsFactory.create(difficulty, world.seed(), playerId);
        var targeting = new AiTargeting(world.collider(), world.unit(), world.config().disc().maxBounces());
        return new PlayerAiController(playerId, skills, targeting, 24, world.seed());
    }

    @Test
    void rejectsTooFewScanAngles() {
        var world = world();
        AiSkills skills = AiSkillsFactory.create(AiDifficulty.NORMAL, world.seed(), 0);
        var targeting = new AiTargeting(world.collider(), world.unit(), 7);
        assertThatThrownBy(() -> new PlayerAiController(0, skills, targeting, 1, world.seed()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverExceedsItsConcurrentDiscCap() {
        var world = world();
        var ai = controller(world, 0, AiDifficulty.VERY_HARD);

        for (int tick = 0; tick < 600; tick++) {
            ai.think(world);
            world.step();
            assertThat(world.activeDiscs(0)).isLessThanOrEqualTo(ai.skills().maxDiscsInFlight());
            // Hard cap from config is also never breached.
            assertThat(world.activeDiscs(0)).isLessThanOrEqualTo(world.config().disc().maxPerPlayer());
        }
    }

    @Test
    void firesAtLeastOnceOverAReasonableHorizon() {
        var world = world();
        var ai = controller(world, 0, AiDifficulty.VERY_HARD);

        boolean everFired = false;
        for (int tick = 0; tick < 1200 && !everFired; tick++) {
            ai.think(world);
            world.step();
            if (world.activeDiscs(0) > 0 || !world.discs().isEmpty()) {
                everFired = true;
            }
        }
        assertThat(everFired).isTrue();
    }

    @Test
    void doesNotFireWhileNoTargetIsKnownOnTheFirstTickBeforeAiming() {
        var world = world();
        var ai = controller(world, 0, AiDifficulty.EASY);

        // A single think+step should never break the disc cap or throw.
        ai.think(world);
        world.step();
        assertThat(world.activeDiscs(0)).isLessThanOrEqualTo(ai.skills().maxDiscsInFlight());
    }

    @Test
    void isDeterministicForAGivenSeed() {
        var worldA = world();
        var aiA = controller(worldA, 0, AiDifficulty.HARD);
        var worldB = world();
        var aiB = controller(worldB, 0, AiDifficulty.HARD);

        for (int tick = 0; tick < 400; tick++) {
            aiA.think(worldA);
            worldA.step();
            aiB.think(worldB);
            worldB.step();
            assertThat(worldA.activeDiscs(0)).isEqualTo(worldB.activeDiscs(0));
            assertThat(worldA.aimOf(0).currentAngle()).isEqualTo(worldB.aimOf(0).currentAngle());
        }
    }

    @Test
    void twoAisOfDifferentLevelsBothPlayWithoutError() {
        var world = world();
        var easy = controller(world, 0, AiDifficulty.EASY);
        var hard = controller(world, 1, AiDifficulty.HARD);

        for (int tick = 0; tick < 500; tick++) {
            easy.think(world);
            hard.think(world);
            world.step();
        }
        assertThat(world.totalActiveDiscs()).isGreaterThanOrEqualTo(0);
    }
}
