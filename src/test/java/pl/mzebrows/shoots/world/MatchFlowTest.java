// src/test/java/pl/mzebrows/shoots/world/MatchFlowTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.score.CaptureScoring;
import pl.mzebrows.shoots.score.PlayerScore;

/** Graphics-free tests for the live round/match flow that replaces the legacy Round/GameSettings path. */
class MatchFlowTest {

    private MatchFlow flow(int players, int roundLimit) {
        return new MatchFlow(players, new RoundConfig(15, roundLimit, 2, 1));
    }

    @Test
    void syncsCurrentPointsFromCaptureScoring() {
        var scoring = new CaptureScoring();
        // Player 0 captures a point at (3,3); each hit awards one level.
        scoring.register(3, 3);
        scoring.resolveHit(3, 3, 0);

        var flow = flow(2, 2);
        flow.syncCurrentPoints(scoring);

        assertThat(flow.scoreOf(0).getCurrentPoints()).isEqualTo(scoring.pointsFor(0));
        assertThat(flow.scoreOf(0).getCurrentPoints()).isPositive();
        assertThat(flow.scoreOf(1).getCurrentPoints()).isZero();
    }

    @Test
    void finishRoundBanksPointsAndAwardsTopScorer() {
        var flow = flow(2, 2);
        flow.scoreOf(0).setCurrentPoints(5);
        flow.scoreOf(1).setCurrentPoints(3);

        flow.finishRound();

        assertThat(flow.roundsPlayed()).isEqualTo(1);
        assertThat(flow.scoreOf(0).getTotalPoints()).isEqualTo(5);
        assertThat(flow.scoreOf(0).getRoundsWon()).isEqualTo(1);
        assertThat(flow.scoreOf(1).getRoundsWon()).isZero();
    }

    @Test
    void matchIsOverOnlyAfterTheConfiguredRoundLimit() {
        var flow = flow(2, 2);
        assertThat(flow.isMatchOver()).isFalse();
        flow.finishRound();
        assertThat(flow.isMatchOver()).isFalse();
        flow.finishRound();
        assertThat(flow.isMatchOver()).isTrue();
    }

    @Test
    void resolveWinnersFlagsThePlayerWithMostRoundsWon() {
        var flow = flow(2, 2);
        flow.scoreOf(0).setCurrentPoints(7);
        flow.scoreOf(1).setCurrentPoints(2);
        flow.finishRound();              // player 0 wins round 1
        flow.scoreOf(0).setCurrentPoints(1);
        flow.scoreOf(1).setCurrentPoints(9);
        flow.finishRound();              // player 1 wins round 2 -> 1 round each, totals 8 vs 11

        var winners = flow.resolveWinners();

        assertThat(winners).extracting(PlayerScore::getPlayerId).containsExactly(1);
        assertThat(flow.scoreOf(1).isWinner()).isTrue();
        assertThat(flow.scoreOf(0).isWinner()).isFalse();
    }

    @Test
    void resetRoundClearsCurrentPointsButKeepsTotalsAndRoundsWon() {
        var flow = flow(2, 2);
        flow.scoreOf(0).setCurrentPoints(6);
        flow.finishRound();

        flow.resetRound();

        assertThat(flow.scoreOf(0).getCurrentPoints()).isZero();
        assertThat(flow.scoreOf(0).getTotalPoints()).isEqualTo(6);
        assertThat(flow.scoreOf(0).getRoundsWon()).isEqualTo(1);
        assertThat(flow.roundsPlayed()).isEqualTo(1);
    }

    @Test
    void resetMatchZeroesEveryTallyAndRoundCounter() {
        var flow = flow(2, 2);
        flow.scoreOf(0).setCurrentPoints(6);
        flow.finishRound();

        flow.resetMatch();

        assertThat(flow.roundsPlayed()).isZero();
        assertThat(flow.scoreOf(0).getTotalPoints()).isZero();
        assertThat(flow.scoreOf(0).getRoundsWon()).isZero();
        assertThat(flow.scoreOf(0).getCurrentPoints()).isZero();
        assertThat(flow.scoreOf(0).isWinner()).isFalse();
    }
}
