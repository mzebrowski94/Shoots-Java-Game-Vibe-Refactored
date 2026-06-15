
package pl.mzebrows.shoots.game.logic;

import java.util.ArrayList;

/**
 * Lightweight per-round timing state read by {@code PlayingState} and the panels: the round number,
 * elapsed round time, and end-of-round delay bookkeeping. Scoring and win resolution now live in the
 * {@code score}/{@code world} model ({@code MatchFlow}/{@code MatchScorer}); this class no longer
 * owns points or capture state.
 */
public class Round {

    int roundNumber;
    ArrayList<Integer> playerPointsList;
    int roundTime;
    boolean roundEnd;

    /** Extra time the round runs past its limit while in-flight discs settle. */
    public int roundEndTimeDelay;
    boolean animationEnded;
    GameSettings gS;

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
        playerPointsList = new ArrayList<>();
    }

    /** Advances the round clock; flags the round as ended once it reaches the configured duration. */
    public void roundTick() {
        roundTime++;
        if (roundTime >= gS.getRoundTime()) {
            roundEnd = true;
        }
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public ArrayList<Integer> getPlayerPointsList() {
        return playerPointsList;
    }

    public void setPlayerPointsList(ArrayList<Integer> playerPointsList) {
        this.playerPointsList = playerPointsList;
    }

    public int getRoundTime() {
        return roundTime;
    }

    public void setRoundTime(int roundTime) {
        this.roundTime = roundTime;
    }

    public boolean isRoundEnd() {
        return roundEnd;
    }

    public void setRoundEnd(boolean roundEnd) {
        this.roundEnd = roundEnd;
    }

    public int getRoundEndTimeDelay() {
        return roundEndTimeDelay;
    }

    public void setRoundEndTimeDelay(int roundEndTimeDelay) {
        this.roundEndTimeDelay = roundEndTimeDelay;
    }

    public boolean isAnimationEnded() {
        return animationEnded;
    }

    public void setAnimationEnded(boolean animationEnded) {
        this.animationEnded = animationEnded;
    }
}
