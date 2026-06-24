// src/test/java/pl/mzebrows/shoots/score/CapturePointStrengthTest.java
package pl.mzebrows.shoots.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Feature #3: a power-shot hit applies several capture levels per hit ({@code captureStrength}),
 * so a charged disc captures/retakes a control point faster than a normal disc.
 */
class CapturePointStrengthTest {

    @Test
    void powerHitCapturesMultipleLevelsAtOnce() {
        var point = new CapturePoint(3, 3);

        boolean changed = point.tryCapture(0, 3); // strength-3 hit on a neutral point

        assertThat(changed).isTrue();
        assertThat(point.isCaptured()).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(0);
        assertThat(point.getLevel()).isEqualTo(3);
    }

    @Test
    void powerHitErodesThenRetakesAnEnemyPointInOneHit() {
        var point = new CapturePoint(3, 3);
        point.tryCapture(0);       // P0 captures...
        point.tryCapture(0);       // ...and levels it to 2
        assertThat(point.getLevel()).isEqualTo(2);

        // Strength-2 enemy hit: erode the last owned level (2 -> 1), then retake at level 1 -- in one hit.
        boolean changed = point.tryCapture(1, 2);

        assertThat(changed).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(1);
        assertThat(point.getLevel()).isEqualTo(1);
    }

    @Test
    void strengthStopsEarlyWhenOwnerHasMaxedThePoint() {
        var point = new CapturePoint(3, 3);
        // A big strength caps out at MAX_LEVEL rather than overshooting.
        point.tryCapture(0, 99);

        assertThat(point.getLevel()).isEqualTo(CapturePoint.MAX_LEVEL);
    }

    @Test
    void strengthOneMatchesASingleNormalHit() {
        var a = new CapturePoint(1, 1);
        var b = new CapturePoint(1, 1);

        a.tryCapture(2);
        b.tryCapture(2, 1);

        assertThat(a.getLevel()).isEqualTo(b.getLevel());
        assertThat(a.getOwnerId()).isEqualTo(b.getOwnerId());
    }

    @Test
    void scoringResolvesHitWithStrength() {
        var scoring = new CaptureScoring();
        scoring.register(5, 5);

        assertThat(scoring.resolveHit(5, 5, 0, 2)).isTrue();
        assertThat(scoring.at(5, 5).getLevel()).isEqualTo(2);
        // A miss (no point at the tile) is still a no-op.
        assertThat(scoring.resolveHit(6, 6, 0, 2)).isFalse();
    }
}
