// src/main/java/pl/mzebrows/shoots/score/MatchScorer.java
package pl.mzebrows.shoots.score;

import java.util.List;
import pl.mzebrows.shoots.config.RoundConfig;

/**
 * AWT-free round/match win resolution over a list of {@link PlayerScore}s, extracting the logic from
 * the legacy {@code Round.checkRoundWinner} and {@code GameSettings.checkGameEnd}.
 *
 * <p>Round end: every player banks this round's points; the highest round score wins a round (ties
 * award all tied players, matching the legacy behaviour). Match end: triggered once the configured
 * round limit is reached; the winner has the most rounds won, broken by cumulative points (ties mark
 * all co-leaders as winners). Decoupled from the {@code GameSettings} GOD class and fully testable.
 */
public final class MatchScorer {

    private final RoundConfig roundConfig;

    public MatchScorer(RoundConfig roundConfig) {
        this.roundConfig = roundConfig;
    }

    /**
     * Finalises a round: banks each player's round points, then awards a round win to every player
     * holding the top round score (ties award all). No-op safe for an empty list.
     */
    public void finishRound(List<PlayerScore> scores) {
        for (PlayerScore score : scores) {
            score.bankRound();
        }
        int best = 0;
        for (PlayerScore score : scores) {
            best = Math.max(best, score.getCurrentPoints());
        }
        for (PlayerScore score : scores) {
            if (score.getCurrentPoints() == best) {
                score.awardRound();
            }
        }
    }

    /** Whether the match has ended: the played round count has reached the configured limit. */
    public boolean isMatchOver(int roundsPlayed) {
        return roundsPlayed >= roundConfig.roundLimit();
    }

    /**
     * Resolves match winners: most rounds won, tie-broken by cumulative points. Marks every
     * co-leader as a winner and returns them. Returns an empty list for no scores.
     */
    public List<PlayerScore> resolveMatchWinners(List<PlayerScore> scores) {
        int mostRounds = 0;
        for (PlayerScore score : scores) {
            mostRounds = Math.max(mostRounds, score.getRoundsWon());
        }
        int bestPoints = 0;
        for (PlayerScore score : scores) {
            if (score.getRoundsWon() == mostRounds) {
                bestPoints = Math.max(bestPoints, score.getTotalPoints());
            }
        }
        var winners = new java.util.ArrayList<PlayerScore>();
        for (PlayerScore score : scores) {
            if (score.getRoundsWon() == mostRounds && score.getTotalPoints() == bestPoints) {
                score.markWinner();
                winners.add(score);
            }
        }
        return winners;
    }
}
