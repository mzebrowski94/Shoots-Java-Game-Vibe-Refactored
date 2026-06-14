// src/test/java/pl/mzebrows/shoots/score/MatchScorerTest.java
package pl.mzebrows.shoots.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.RoundConfig;

/** Graphics-free tests for round-win, match-end, and tie resolution. */
class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer(new RoundConfig(15, 2, 2, 1));

    private PlayerScore score(int id, int currentPoints) {
        var s = new PlayerScore(id);
        s.setCurrentPoints(currentPoints);
        return s;
    }

    @Test
    void finishRoundBanksPointsAndAwardsTopScorer() {
        var p1 = score(1, 5);
        var p2 = score(2, 3);

        scorer.finishRound(List.of(p1, p2));

        assertThat(p1.getTotalPoints()).isEqualTo(5);
        assertThat(p2.getTotalPoints()).isEqualTo(3);
        assertThat(p1.getRoundsWon()).isEqualTo(1);
        assertThat(p2.getRoundsWon()).isZero();
    }

    @Test
    void finishRoundAwardsAllTiedLeaders() {
        var p1 = score(1, 4);
        var p2 = score(2, 4);

        scorer.finishRound(List.of(p1, p2));

        assertThat(p1.getRoundsWon()).isEqualTo(1);
        assertThat(p2.getRoundsWon()).isEqualTo(1);
    }

    @Test
    void matchIsOverOnceRoundLimitReached() {
        assertThat(scorer.isMatchOver(1)).isFalse();
        assertThat(scorer.isMatchOver(2)).isTrue();
        assertThat(scorer.isMatchOver(3)).isTrue();
    }

    @Test
    void matchWinnerHasMostRoundsWon() {
        var p1 = new PlayerScore(1);
        var p2 = new PlayerScore(2);
        p1.awardRound();
        p1.awardRound();
        p2.awardRound();

        List<PlayerScore> winners = scorer.resolveMatchWinners(List.of(p1, p2));

        assertThat(winners).containsExactly(p1);
        assertThat(p1.isWinner()).isTrue();
        assertThat(p2.isWinner()).isFalse();
    }

    @Test
    void roundsTieBrokenByTotalPoints() {
        var p1 = new PlayerScore(1);
        var p2 = new PlayerScore(2);
        p1.awardRound();
        p2.awardRound();
        p1.setCurrentPoints(10);
        p1.bankRound();
        p2.setCurrentPoints(6);
        p2.bankRound();

        List<PlayerScore> winners = scorer.resolveMatchWinners(List.of(p1, p2));

        assertThat(winners).containsExactly(p1);
    }

    @Test
    void fullTieMarksBothWinners() {
        var p1 = new PlayerScore(1);
        var p2 = new PlayerScore(2);
        p1.awardRound();
        p2.awardRound();
        p1.setCurrentPoints(8);
        p1.bankRound();
        p2.setCurrentPoints(8);
        p2.bankRound();

        List<PlayerScore> winners = scorer.resolveMatchWinners(List.of(p1, p2));

        assertThat(winners).containsExactlyInAnyOrder(p1, p2);
    }
}
