// src/test/java/pl/mzebrows/shoots/ai/AiSkillsFactoryTest.java
package pl.mzebrows.shoots.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiSkillsFactoryTest {

    private static final long SEED = 123456789L;

    @Test
    void deterministicForSameInputs() {
        var a = AiSkillsFactory.create(AiDifficulty.HARD, SEED, 1);
        var b = AiSkillsFactory.create(AiDifficulty.HARD, SEED, 1);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentPlayersGetIndependentDraws() {
        var p1 = AiSkillsFactory.create(AiDifficulty.NORMAL, SEED, 1);
        var p2 = AiSkillsFactory.create(AiDifficulty.NORMAL, SEED, 2);

        // Same level but different slots: the per-AI deviation should make them differ somewhere.
        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    void differentSeedsGiveDifferentSkills() {
        var s1 = AiSkillsFactory.create(AiDifficulty.NORMAL, 1L, 1);
        var s2 = AiSkillsFactory.create(AiDifficulty.NORMAL, 2L, 1);

        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void accuracyIsMonotonicAcrossTheLadder() {
        // Average over several seeds so the small per-AI deviation can't flip the ordering.
        double easy = avgAccuracy(AiDifficulty.EASY);
        double normal = avgAccuracy(AiDifficulty.NORMAL);
        double hard = avgAccuracy(AiDifficulty.HARD);
        double veryHard = avgAccuracy(AiDifficulty.VERY_HARD);

        assertThat(easy).isLessThan(normal);
        assertThat(normal).isLessThan(hard);
        assertThat(hard).isLessThan(veryHard);
    }

    @Test
    void reactionTimeShortensAcrossTheLadder() {
        var easy = AiSkillsFactory.create(AiDifficulty.EASY, SEED, 1);
        var veryHard = AiSkillsFactory.create(AiDifficulty.VERY_HARD, SEED, 1);

        // Smaller interval = faster reactions; strongest must react no slower than weakest.
        assertThat(veryHard.decisionIntervalTicks()).isLessThan(easy.decisionIntervalTicks());
        assertThat(veryHard.volleyCooldownTicks()).isLessThan(easy.volleyCooldownTicks());
    }

    @Test
    void randomStaysWithinValidBounds() {
        // Every RANDOM draw must still satisfy the AiSkills invariants (constructor would throw otherwise)
        // and keep normalised knobs inside [0,1].
        for (int seed = 0; seed < 200; seed++) {
            var s = AiSkillsFactory.create(AiDifficulty.RANDOM, seed, seed % 4);
            assertThat(s.accuracy()).isBetween(0.0, 1.0);
            assertThat(s.cursorSpeedFactor()).isBetween(0.0, 1.0);
            assertThat(s.retakeStubbornness()).isBetween(0.0, 1.0);
            assertThat(s.defendTendency()).isBetween(0.0, 1.0);
            assertThat(s.bouncePathPreference()).isBetween(0.0, 1.0);
            assertThat(s.maxDiscsInFlight()).isGreaterThanOrEqualTo(1);
            assertThat(s.maxDiscsPerShot()).isGreaterThanOrEqualTo(1);
            assertThat(s.decisionIntervalTicks()).isGreaterThanOrEqualTo(1);
            assertThat(s.volleyCooldownTicks()).isGreaterThanOrEqualTo(0);
            assertThat(s.targetMode()).isNotNull();
        }
    }

    @Test
    void randomIsAlsoDeterministicForSameInputs() {
        var a = AiSkillsFactory.create(AiDifficulty.RANDOM, SEED, 3);
        var b = AiSkillsFactory.create(AiDifficulty.RANDOM, SEED, 3);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void normalisedKnobsAlwaysStayInUnitRangeAcrossTheLadder() {
        for (AiDifficulty d : new AiDifficulty[]{AiDifficulty.EASY, AiDifficulty.NORMAL,
                AiDifficulty.HARD, AiDifficulty.VERY_HARD}) {
            for (int playerId = 0; playerId < 4; playerId++) {
                var s = AiSkillsFactory.create(d, SEED, playerId);
                assertThat(s.accuracy()).isBetween(0.0, 1.0);
                assertThat(s.cursorSpeedFactor()).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void aiSkillsRejectsOutOfRangeKnob() {
        assertThatThrownBy(() -> new AiSkills(1.5, 0.5, 1, 1, 0.5, 0.5, 0.5, 10, 10, TargetMode.NEAREST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiSkillsRejectsNonPositiveDecisionInterval() {
        assertThatThrownBy(() -> new AiSkills(0.5, 0.5, 1, 1, 0.5, 0.5, 0.5, 0, 10, TargetMode.NEAREST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double avgAccuracy(AiDifficulty d) {
        double sum = 0;
        int n = 50;
        for (int seed = 0; seed < n; seed++) {
            sum += AiSkillsFactory.create(d, seed, 1).accuracy();
        }
        return sum / n;
    }
}
