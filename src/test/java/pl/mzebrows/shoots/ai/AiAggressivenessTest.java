// src/test/java/pl/mzebrows/shoots/ai/AiAggressivenessTest.java
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
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Task 1.2: AI aggressiveness. A new {@code baseAttackTendency} knob controls how readily an AI targets
 * opponents' bases to disrupt them; it scales up the difficulty ladder. A per-skill toggle (carried in
 * {@code AiConfig.toggles()}) disables the behaviour, and the master {@code ai.baseAttackEnabled} switch
 * gates it for the whole match.
 */
class AiAggressivenessTest {

    // A seed whose generated map gives the bottom AI (P0) a workable bounce path to the top base (P1),
    // so an aggressive AI lands a disruption within the test horizon (deterministic for this seed).
    private static final long SEED = 22L;

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
                new DiscConfig(18, 10, 2.0, 7, 4, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette(), ai);
    }

    /** Averages the knob over many seeds so per-AI deviation can't flip the monotonic ordering. */
    private static double avgBaseAttack(AiDifficulty difficulty) {
        double sum = 0.0;
        int n = 0;
        for (long seed = 1; seed <= 60; seed++) {
            sum += AiSkillsFactory.create(difficulty, seed, 1).baseAttackTendency();
            n++;
        }
        return sum / n;
    }

    @Test
    void baseAttackTendencyRisesAcrossTheLadder() {
        double easy = avgBaseAttack(AiDifficulty.EASY);
        double normal = avgBaseAttack(AiDifficulty.NORMAL);
        double veryHard = avgBaseAttack(AiDifficulty.VERY_HARD);

        assertThat(easy).isLessThan(normal);
        assertThat(normal).isLessThan(veryHard);
        assertThat(easy).isBetween(0.0, 1.0);
        assertThat(veryHard).isBetween(0.0, 1.0);
    }

    @Test
    void baseAttackTendencyIsDeterministicPerSeedAndPlayer() {
        var a = AiSkillsFactory.create(AiDifficulty.HARD, SEED, 2);
        var b = AiSkillsFactory.create(AiDifficulty.HARD, SEED, 2);
        assertThat(a.baseAttackTendency()).isEqualTo(b.baseAttackTendency());
    }

    @Test
    void disablingTheBaseAttackToggleForcesTheKnobToZero() {
        var off = new AiSkillToggles(true, true, true, true, true, true, false);
        var skills = AiSkillsFactory.create(AiDifficulty.VERY_HARD, SEED, 1, off);
        assertThat(skills.baseAttackTendency()).isZero();

        // A different toggle off does NOT zero base attack.
        var powerOff = new AiSkillToggles(true, true, true, true, true, false, true);
        var skills2 = AiSkillsFactory.create(AiDifficulty.VERY_HARD, SEED, 1, powerOff);
        assertThat(skills2.baseAttackTendency()).isGreaterThan(0.0);
        assertThat(skills2.powerShotTendency()).isZero();
    }

    @Test
    void aggressiveAiDisruptsAnOpponentBaseWhenEnabled() {
        // VERY_HARD has a high base-attack tendency; on this seed P0 can reach P1's base, so over a
        // reasonable horizon the aggressive AI lands a disruption (deterministic for this seed).
        var world = new PlayWorld(config(new AiConfig(24, 4, true)), SEED);
        var ai = controller(world, 0, AiDifficulty.VERY_HARD);

        boolean disrupted = stepUntilP1Disrupted(world, ai, 3000);
        assertThat(disrupted).as("aggressive AI eventually disrupts the opponent base").isTrue();
    }

    @Test
    void targetingLayerFindsOpponentBasesOnlyWhenAskedTo() {
        // The AI's base targeting is folded into the same reachability scan it uses for capture points.
        // reachIncludingBases() surfaces an opponent base as a candidate; reach() never does, so a
        // non-aggressive scan can't pick a base at all. Seed 42 leaves a clean direct lane up column 12.
        var world = new PlayWorld(config(new AiConfig(24, 4, true)), 42L);
        var base = world.baseOf(0);
        var targeting = new AiTargeting(world.tracer(), world.config().disc().maxBounces());
        var aim = world.aimOf(0);
        double centre = aim.getShootDirection();
        double limit = aim.getRotationLimit();

        boolean foundBaseWithBases = false;
        boolean foundBaseWithout = false;
        for (int i = 0; i < 360; i++) {
            double angle = centre - limit + (double) i / 359 * 2 * limit;
            var withBases = targeting.reachIncludingBases(base.pixelX(), base.pixelY(), angle, 2.0);
            if (withBases.reached() && withBases.base()) {
                foundBaseWithBases = true;
            }
            var without = targeting.reach(base.pixelX(), base.pixelY(), angle, 2.0);
            if (without.reached() && without.base()) {
                foundBaseWithout = true;
            }
        }
        assertThat(foundBaseWithBases).as("base-aware scan surfaces opponent bases").isTrue();
        assertThat(foundBaseWithout).as("plain capture scan never returns a base").isFalse();
    }

    private static PlayerAiController controller(PlayWorld world, int playerId, AiDifficulty difficulty) {
        var skills = AiSkillsFactory.create(difficulty, world.seed(), playerId, world.config().ai().toggles());
        var targeting = new AiTargeting(world.tracer(), world.config().disc().maxBounces());
        return new PlayerAiController(playerId, skills, targeting, 24, world.seed());
    }

    private static boolean stepUntilP1Disrupted(PlayWorld world, PlayerAiController ai, int ticks) {
        for (int t = 0; t < ticks; t++) {
            ai.think(world);
            world.step();
            if (world.isDisrupted(1)) {
                return true;
            }
        }
        return false;
    }
}
