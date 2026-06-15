// src/test/java/pl/mzebrows/shoots/score/CaptureScoringTest.java
package pl.mzebrows.shoots.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Graphics-free tests for the one-level-per-hit capture/steal/cap rules and tile-indexed resolution. */
class CaptureScoringTest {

    @Test
    void neutralPointAwardsNothing() {
        var point = new CapturePoint(5, 5);
        assertThat(point.isCaptured()).isFalse();
        assertThat(point.awardedPoints()).isZero();
        assertThat(point.getOwnerId()).isEqualTo(CapturePoint.NO_OWNER);
    }

    @Test
    void firstHitCapturesAtLevelOne() {
        var point = new CapturePoint(5, 5);

        assertThat(point.tryCapture(1)).isTrue();
        assertThat(point.isCaptured()).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(1);
        assertThat(point.getLevel()).isEqualTo(1);
        assertThat(point.awardedPoints()).isEqualTo(1);
    }

    @Test
    void eachOwnerHitRaisesLevelByExactlyOne() {
        var point = new CapturePoint(5, 5);

        point.tryCapture(1); // level 1
        point.tryCapture(1); // level 2
        assertThat(point.getLevel()).isEqualTo(2);

        point.tryCapture(1); // level 3
        assertThat(point.getLevel()).isEqualTo(3);
        assertThat(point.awardedPoints()).isEqualTo(3);
    }

    @Test
    void aHitFromAnotherPlayerStealsBackToLevelOne() {
        var point = new CapturePoint(5, 5);
        point.tryCapture(1); // p1 level 1
        point.tryCapture(1); // p1 level 2

        boolean changed = point.tryCapture(2); // steal

        assertThat(changed).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(2);
        assertThat(point.getLevel()).isEqualTo(1);
    }

    @Test
    void ownerHitOnMaxedPointChangesNothing() {
        var point = new CapturePoint(5, 5);
        for (int i = 0; i < CapturePoint.MAX_LEVEL; i++) {
            point.tryCapture(1);
        }
        assertThat(point.getLevel()).isEqualTo(CapturePoint.MAX_LEVEL);

        // Already maxed by the same owner -> no change, disc would pass through.
        assertThat(point.tryCapture(1)).isFalse();
        assertThat(point.getLevel()).isEqualTo(CapturePoint.MAX_LEVEL);
    }

    @Test
    void levelNeverExceedsMaxAcrossManyHits() {
        var point = new CapturePoint(5, 5);
        for (int i = 0; i < 10; i++) {
            point.tryCapture(1);
        }

        assertThat(point.getLevel()).isEqualTo(CapturePoint.MAX_LEVEL);
        assertThat(point.awardedPoints()).isEqualTo(CapturePoint.MAX_LEVEL);
    }

    @Test
    void resolveHitDispatchesByTileIndex() {
        var scoring = new CaptureScoring();
        scoring.register(3, 4);
        scoring.register(7, 8);

        assertThat(scoring.resolveHit(7, 8, 1)).isTrue();
        assertThat(scoring.at(7, 8).getOwnerId()).isEqualTo(1);
        assertThat(scoring.at(3, 4).isCaptured()).isFalse();
    }

    @Test
    void resolveHitOnEmptyTileIsNoOp() {
        var scoring = new CaptureScoring();
        scoring.register(3, 4);

        assertThat(scoring.resolveHit(9, 9, 1)).isFalse();
    }

    @Test
    void resolveHitReturnsFalseWhenNothingChanges() {
        var scoring = new CaptureScoring();
        scoring.register(2, 2);
        for (int i = 0; i < CapturePoint.MAX_LEVEL; i++) {
            scoring.resolveHit(2, 2, 1);
        }

        // Owner hitting a maxed point: no change -> disc passes through.
        assertThat(scoring.resolveHit(2, 2, 1)).isFalse();
    }

    @Test
    void pointsForSumsOnlyThatPlayersCapturedPoints() {
        var scoring = new CaptureScoring();
        scoring.register(1, 1);
        scoring.register(2, 2);
        scoring.register(3, 3);

        scoring.resolveHit(1, 1, 1); // p1 -> point (1,1) level 1
        scoring.resolveHit(1, 1, 1); // p1 -> point (1,1) level 2
        scoring.resolveHit(2, 2, 1); // p1 -> point (2,2) level 1
        scoring.resolveHit(3, 3, 2); // p2 -> point (3,3) level 1

        assertThat(scoring.pointsFor(1)).isEqualTo(3); // 2 + 1
        assertThat(scoring.pointsFor(2)).isEqualTo(1);
        assertThat(scoring.pointsFor(3)).isZero();
    }

    @Test
    void resetAllReturnsPointsToNeutral() {
        var scoring = new CaptureScoring();
        scoring.register(1, 1);
        scoring.resolveHit(1, 1, 1);

        scoring.resetAll();

        assertThat(scoring.at(1, 1).isCaptured()).isFalse();
        assertThat(scoring.pointsFor(1)).isZero();
    }
}
