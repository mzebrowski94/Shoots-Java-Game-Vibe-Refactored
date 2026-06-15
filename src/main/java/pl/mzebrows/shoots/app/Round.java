// pl/mzebrows/shoots/app/Round.java
package pl.mzebrows.shoots.app;

import lombok.Getter;
import lombok.Setter;

/**
 * Lightweight per-round timing state read by {@code PlayingState} and the panels: the round number,
 * elapsed round time, and end-of-round delay bookkeeping. Scoring and win resolution now live in the
 * {@code score}/{@code world} model ({@code MatchFlow}/{@code MatchScorer}); this class no longer
 * owns points or capture state.
 */
@Getter
@Setter
public class Round {

    private int roundNumber;
    private int roundTime;
    private boolean roundEnd;

    /** Extra time the round runs past its limit while in-flight discs settle. */
    private int roundEndTimeDelay;
    private boolean animationEnded;

    private final GameSettings gS;

    /**
     * @param gameSettings shared settings (round duration)
     * @param roundNumber  this round's 1-based index
     */
    public Round(GameSettings gameSettings, int roundNumber) {
        gS = gameSettings;
        this.roundNumber = roundNumber;
        this.roundEnd = false;
        this.roundEndTimeDelay = 0;
        this.animationEnded = false;
        this.roundTime = 0;
    }

    /** Advances the round clock; flags the round as ended once it reaches the configured duration. */
    public void roundTick() {
        roundTime++;
        if (roundTime >= gS.getRoundTime()) {
            roundEnd = true;
        }
    }
}
