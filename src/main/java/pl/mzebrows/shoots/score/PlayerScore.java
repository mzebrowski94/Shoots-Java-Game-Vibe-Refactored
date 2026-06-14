// src/main/java/pl/mzebrows/shoots/score/PlayerScore.java
package pl.mzebrows.shoots.score;

import lombok.Getter;

/**
 * AWT-free per-player score tally, extracted from the legacy {@code Player} score fields: current
 * round points, cumulative match points, and rounds won. Pure state + mutators so match logic is
 * unit-testable without the {@code GameSettings} GOD class or any graphics context.
 */
@Getter
public final class PlayerScore {

    private final int playerId;

    private int currentPoints;
    private int totalPoints;
    private int roundsWon;
    private boolean winner;

    public PlayerScore(int playerId) {
        this.playerId = playerId;
    }

    /** Sets the points controlled this round (recomputed from capture state each tick). */
    public void setCurrentPoints(int points) {
        this.currentPoints = points;
    }

    /** Folds this round's points into the cumulative match total. */
    public void bankRound() {
        totalPoints += currentPoints;
    }

    /** Records that this player won a round. */
    public void awardRound() {
        roundsWon++;
    }

    /** Flags this player as the overall match winner. */
    public void markWinner() {
        winner = true;
    }

    /** Clears the current-round points (e.g. on round start). */
    public void resetCurrentPoints() {
        currentPoints = 0;
    }
}
