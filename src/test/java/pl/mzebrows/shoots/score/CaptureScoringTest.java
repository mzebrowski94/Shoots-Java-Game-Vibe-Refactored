// src/test/java/pl/mzebrows/shoots/score/CaptureScoringTest.java
package pl.mzebrows.shoots.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Graphics-free tests for capture/steal/cap rules and tile-indexed hit resolution. */
class CaptureScoringTest {

    @Test
    void neutralPointAwardsNothing() {
        var point = new CapturePoint(5, 5);
        assertThat(point.isCaptured()).isFalse();
        assertThat(point.awardedPoints()).isZero();
        assertThat(point.getOwnerId()).isEqualTo(CapturePoint.NO_OWNER);
    }

    @Test
    void higherBounceCountCapturesAndRaisesLevel() {
        var point = new CapturePoint(5, 5);

        assertThat(point.tryCapture(1, 2)).isTrue();
        assertThat(point.isCaptured()).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(1);
        assertThat(point.getLevel()).isEqualTo(2);
        assertThat(point.awardedPoints()).isEqualTo(2);
    }

    @Test
    void equalCountByDifferentPlayerSteals() {
        var point = new CapturePoint(5, 5);
        point.tryCapture(1, 2);

        boolean changed = point.tryCapture(2, 2);

        assertThat(changed).isTrue();
        assertThat(point.getOwnerId()).isEqualTo(2);
        assertThat(point.getLevel()).isEqualTo(2);
    }

    @Test
    void equalCountBySamePlayerDoesNothing() {
        var point = new CapturePoint(5, 5);
        point.tryCapture(1, 2);

        assertThat(point.tryCapture(1, 2)).isFalse();
        assertThat(point.getOwnerId()).isEqualTo(1);
    }

    @Test
    void lowerBounceCountDoesNotCapture() {
        var point = new CapturePoint(5, 5);
        point.tryCapture(1, 3);

        assertThat(point.tryCapture(2, 2)).isFalse();
        assertThat(point.getOwnerId()).isEqualTo(1);
        assertThat(point.getLevel()).isEqualTo(3);
    }

    @Test
    void levelIsCappedAtMax() {
        var point = new CapturePoint(5, 5);

        point.tryCapture(1, 10);

        assertThat(point.getLevel()).isEqualTo(CapturePoint.MAX_LEVEL);
        assertThat(point.awardedPoints()).isEqualTo(CapturePoint.MAX_LEVEL);
    }

    @Test
    void resolveHitDispatchesByTileIndex() {
        var scoring = new CaptureScoring();
        scoring.register(3, 4);
        scoring.register(7, 8);

        assertThat(scoring.resolveHit(7, 8, 1, 2)).isTrue();
        assertThat(scoring.at(7, 8).getOwnerId()).isEqualTo(1);
        assertThat(scoring.at(3, 4).isCaptured()).isFalse();
    }

    @Test
    void resolveHitOnEmptyTileIsNoOp() {
        var scoring = new CaptureScoring();
        scoring.register(3, 4);

        assertThat(scoring.resolveHit(9, 9, 1, 5)).isFalse();
    }

    @Test
    void pointsForSumsOnlyThatPlayersCapturedPoints() {
        var scoring = new CaptureScoring();
        scoring.register(1, 1);
        scoring.register(2, 2);
        scoring.register(3, 3);

        scoring.resolveHit(1, 1, 1, 3); // player 1 -> 3 pts
        scoring.resolveHit(2, 2, 1, 1); // player 1 -> 1 pt
        scoring.resolveHit(3, 3, 2, 2); // player 2 -> 2 pts

        assertThat(scoring.pointsFor(1)).isEqualTo(4);
        assertThat(scoring.pointsFor(2)).isEqualTo(2);
        assertThat(scoring.pointsFor(3)).isZero();
    }

    @Test
    void resetAllReturnsPointsToNeutral() {
        var scoring = new CaptureScoring();
        scoring.register(1, 1);
        scoring.resolveHit(1, 1, 1, 3);

        scoring.resetAll();

        assertThat(scoring.at(1, 1).isCaptured()).isFalse();
        assertThat(scoring.pointsFor(1)).isZero();
    }
}
