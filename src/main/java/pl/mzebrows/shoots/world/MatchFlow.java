// src/main/java/pl/mzebrows/shoots/world/MatchFlow.java
package pl.mzebrows.shoots.world;

import java.util.ArrayList;
import java.util.List;

import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.score.CaptureScoring;
import pl.mzebrows.shoots.score.MatchScorer;
import pl.mzebrows.shoots.score.PlayerScore;

/**
 * Live round/match scoring driver: owns one {@link PlayerScore} per player and the AWT-free
 * {@link MatchScorer}, replacing the legacy {@code Round.checkRoundWinner} +
 * {@code GameSettings.checkGameEnd} GOD-class path.
 *
 * <p>Each step the current-round points are refreshed from {@link CaptureScoring} (the authoritative
 * capture state), so {@link #scoreOf(int)} is the single queryable contract the render layer reads.
 * Round end banks points and awards the round winner(s); match end is decided once the configured
 * round limit is reached, tie-broken by cumulative points.
 */
public final class MatchFlow {

    private final MatchScorer scorer;
    private final List<PlayerScore> scores;
    private int roundsPlayed;

    public MatchFlow(int playerCount, RoundConfig roundConfig) {
        this.scorer = new MatchScorer(roundConfig);
        this.scores = new ArrayList<>(playerCount);
        for (int p = 0; p < playerCount; p++) {
            scores.add(new PlayerScore(p));
        }
    }

    /** Refreshes every player's current-round points from the authoritative capture state. */
    public void syncCurrentPoints(CaptureScoring scoring) {
        for (PlayerScore score : scores) {
            score.setCurrentPoints(scoring.pointsFor(score.getPlayerId()));
        }
    }

    /**
     * Finalises the current round: counts it as played, banks each player's points, and awards the
     * round to the top scorer(s). Call once per round transition into the ENDS phase.
     */
    public void finishRound() {
        roundsPlayed++;
        scorer.finishRound(scores);
    }

    /** Whether the configured round limit has been reached. */
    public boolean isMatchOver() {
        return scorer.isMatchOver(roundsPlayed);
    }

    /** Resolves and flags the overall match winner(s): most rounds won, tie-broken by total points. */
    public List<PlayerScore> resolveWinners() {
        return scorer.resolveMatchWinners(scores);
    }

    /** Clears the current-round points for a new round (cumulative totals and rounds won persist). */
    public void resetRound() {
        for (PlayerScore score : scores) {
            score.resetCurrentPoints();
        }
    }

    /** Resets the entire match (new game): zeroes every tally and the played-round counter. */
    public void resetMatch() {
        scores.replaceAll(score -> new PlayerScore(score.getPlayerId()));
        roundsPlayed = 0;
    }

    // -- queries (read by the renderer / state) -----------------------------

    /** Number of rounds that have been finalised so far. */
    public int roundsPlayed() { return roundsPlayed; }

    /** The score tally for a player. */
    public PlayerScore scoreOf(int playerId) { return scores.get(playerId); }

    /** All player score tallies, in player-id order. */
    public List<PlayerScore> scores() { return scores; }
}
