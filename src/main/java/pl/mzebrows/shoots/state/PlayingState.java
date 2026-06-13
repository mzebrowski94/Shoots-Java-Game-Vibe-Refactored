// pl/mzebrows/shoots/state/PlayingState.java
package pl.mzebrows.shoots.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.game.logic.ColisionPoint;
import pl.mzebrows.shoots.game.logic.GameCounter;
import pl.mzebrows.shoots.game.logic.GameFrame;
import pl.mzebrows.shoots.game.logic.GamePointer;
import pl.mzebrows.shoots.game.logic.GameScreen;
import pl.mzebrows.shoots.game.logic.GameSettings;
import pl.mzebrows.shoots.game.logic.Player;
import pl.mzebrows.shoots.game.logic.RoundEnum;
import pl.mzebrows.shoots.game.logic.Drawables.LightEffect;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * Active gameplay state: manages the ROUND_BEGIN → ROUND_CONTINUES → ROUND_ENDS cycle.
 * Transitions out to {@link PausedState} on {@link GameAction#PAUSE} or to
 * {@link GameOverState} when the match ends.
 */
public final class PlayingState implements GameState {

    private static final Logger log = LoggerFactory.getLogger(PlayingState.class);

    private enum Phase { BEGIN, CONTINUES, ENDS }

    private final GameSettings settings;
    private final GameScreen screen;
    private final GameCounter counter;
    private final GamePointer pointer;

    private GameState pausedState;
    private GameState gameOverState;

    private Phase phase = Phase.BEGIN;
    private boolean phaseJustEntered = true;
    private boolean requestNextPhase = false;

    // Tracks elapsed time for 1-second round ticks
    private long lastSecondNano = System.nanoTime();

    private boolean restartRequested = false;

    public PlayingState(GameSettings settings, GameFrame frame) {
        this.settings = settings;
        this.screen  = frame.getGameScreen();
        this.counter = frame.getGameCounter();
        this.pointer = frame.getGamePointer();
    }

    /** Must be called after construction to complete the object graph. */
    public void setPausedState(GameState pausedState) { this.pausedState = pausedState; }

    /** Must be called after construction to complete the object graph. */
    public void setGameOverState(GameState gameOverState) { this.gameOverState = gameOverState; }

    /**
     * Requests that the next BEGIN phase restarts the full match instead of starting a new round.
     * Also resets the internal phase so we always start from BEGIN.
     */
    public void requestRestart() {
        restartRequested = true;
        phase = Phase.BEGIN;
        phaseJustEntered = true;
        requestNextPhase = false;
    }

    @Override
    public void enter() {
        lastSecondNano = System.nanoTime();
    }

    @Override
    public GameState update(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            return pausedState;
        }

        return switch (phase) {
            case BEGIN -> updateBegin();
            case CONTINUES -> updateContinues(input);
            case ENDS -> updateEnds();
        };
    }

    @Override
    public void exit() { /* retain phase so pause/resume works */ }

    /** Current phase as {@link RoundEnum} for the legacy renderer. */
    public RoundEnum getRenderRoundEnum() {
        return switch (phase) {
            case BEGIN -> RoundEnum.ROUND_BEGIN;
            case CONTINUES -> RoundEnum.ROUND_CONTINUES;
            case ENDS -> RoundEnum.ROUND_ENDS;
        };
    }

    // -------------------------------------------------------------------------
    // Phase handlers

    private GameState updateBegin() {
        counter.tick();
        if (phaseJustEntered) {
            if (restartRequested) {
                doRestartGame();
                restartRequested = false;
            } else {
                settings.startNewRound(screen);
            }
            phaseJustEntered = false;
        }
        screen.tick();
        if (animationsEnded()) {
            restartAnimations();
            requestNextPhase = false;
            phase = Phase.CONTINUES;
            phaseJustEntered = true;
        }
        return this;
    }

    private GameState updateContinues(InputBridge input) {
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(true);
            phaseJustEntered = false;
            lastSecondNano = System.nanoTime();
        }
        counter.tick();

        long now = System.nanoTime();
        if (now - lastSecondNano >= 1_000_000_000L) {
            lastSecondNano = now;
            tickRoundSecond();
        }

        updateGameLogic();
        if (settings.isPlayerKeyboardAvailable()) {
            settings.checkPlayerInput(input);
        }
        if (requestNextPhase) {
            phase = Phase.ENDS;
            phaseJustEntered = true;
        }
        return this;
    }

    private GameState updateEnds() {
        counter.tick();
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(false);
            phaseJustEntered = false;
            settings.getActualRound().savePlayerPoints();
            settings.getActualRound().checkRoundWinner();
        }
        screen.tick();
        pointer.tick();
        if (animationsEnded()) {
            restartAnimations();
            requestNextPhase = false;
            if (settings.checkGameEnd()) {
                return gameOverState;
            }
            phase = Phase.BEGIN;
            phaseJustEntered = true;
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Helpers

    private void tickRoundSecond() {
        settings.getActualRound().roundTick();
        if (settings.getActualRound().isRoundEnd()) {
            settings.setPlayerKeyboardAvailable(false);
            if (countActiveDiscs() == 0) {
                settings.getActualRound().roundEndTimeDelay++;
                if (settings.getActualRound().getRoundEndTimeDelay() >= settings.getRoundEndDelay()) {
                    requestNextPhase = true;
                }
            }
        }
    }

    private void updateGameLogic() {
        for (int i = 0; i < settings.getPlayerList().size(); i++) {
            Player player = settings.getPlayerList().get(i);
            player.getPlayerLaser().moveLaser();
            for (int j = 0; j < player.getPlayerDiscs().size(); j++) {
                player.getPlayerDiscs().get(j).moveDisc();
                ColisionPoint cp = player.getPlayerDiscs().get(j).checkCollision();
                if (cp.isColision()) {
                    if (player.getPlayerDiscs().get(j).checkColisionsNumber()) {
                        player.removeDisc(j);
                    } else if (cp.getColisionType() == 0) {
                        if (screen.setPointField(cp, player,
                                player.getPlayerDiscs().get(j).getColisionTimes())) {
                            player.removeDisc(j);
                        }
                    } else {
                        screen.getEffectList().add(
                                new LightEffect(player.getColor(), cp));
                    }
                }
            }
        }
    }

    private int countActiveDiscs() {
        int count = 0;
        for (int i = 0; i < settings.getPlayerList().size(); i++) {
            count += settings.getPlayerList().get(i).getPlayerDiscs().size();
        }
        return count;
    }

    private boolean animationsEnded() {
        return screen.isAnimationElementEnd()
                && counter.isAnimationElementEnd();
    }

    private void restartAnimations() {
        counter.restartAnimation();
        pointer.restartAnimation();
        screen.restartAnimation();
    }

    private void doRestartGame() {
        settings.restartGame();
        pointer.restartGamePointer();
        counter.restartAnimationTime();
        settings.startNewRound(screen);
        log.info("Game restarted");
    }
}
