// pl/mzebrows/shoots/game/logic/GameLoop.java
package pl.mzebrows.shoots.game.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.state.GameOverState;
import pl.mzebrows.shoots.state.GameStateMachine;
import pl.mzebrows.shoots.state.PausedState;
import pl.mzebrows.shoots.state.PlayingState;

/**
 * Top-level game loop. Owns the timing mechanism (simple sleep-based, replaced by
 * fixed-timestep accumulator in cluster 3) and drives the {@link GameStateMachine}.
 */
public final class GameLoop {

    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private static final int TARGET_FPS = 120;
    private static final long OPTIMAL_TIME = 1_000_000_000L / TARGET_FPS;

    private final GameSettings gameSettings = new GameSettings();
    private GameFrame gameFrame;
    private GameStateMachine stateMachine;

    private long lastLoopTime = System.nanoTime();
    private long lastFpsTime = 0;

    /** Entry point: initialises and runs the game loop until the state machine quits. */
    public GameLoop() {
        log.info("Starting GameLoop");
        initializeGraphics();
        initializeLogic();

        while (stateMachine.isRunning()) {
            long now = System.nanoTime();
            long updateLength = now - lastLoopTime;
            lastLoopTime = now;
            lastFpsTime += updateLength;

            if (lastFpsTime >= OPTIMAL_TIME) {
                var input = gameSettings.getInputBridge();
                input.poll();
                stateMachine.update(input);

                var renderEnum = switch (stateMachine.current()) {
                    case PlayingState ps -> ps.getRenderRoundEnum();
                    default -> RoundEnum.ROUND_PAUSED;
                };
                gameRenderUpdate(renderEnum);

                lastFpsTime -= OPTIMAL_TIME;
            }

            long sleepTime = (lastLoopTime - System.nanoTime() + OPTIMAL_TIME) / 1_000_000L;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    log.error("Game loop interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        gameFrame.dispose();
        log.info("GameLoop finished");
    }

    // -------------------------------------------------------------------------

    private void initializeGraphics() {
        gameFrame = new GameFrame(gameSettings);
    }

    private void initializeLogic() {
        var playingState = new PlayingState(gameSettings, gameFrame);
        var pausedState  = new PausedState(gameSettings, gameFrame.gameScreen, playingState);
        var gameOverState = new GameOverState(gameSettings, gameFrame.gameScreen, playingState);

        playingState.setPausedState(pausedState);
        playingState.setGameOverState(gameOverState);

        gameSettings.setActualRoundNumber(0);

        stateMachine = new GameStateMachine(pausedState);
    }

    private void gameRenderUpdate(RoundEnum roundState) {
        gameFrame.gameCounter.drawUpdate(roundState);
        gameFrame.gamePointer.drawUpdate(roundState);
        gameFrame.gameScreen.drawUpdate(roundState);
    }
}
